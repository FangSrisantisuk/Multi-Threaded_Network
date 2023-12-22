
import java.io.*;
import java.net.*;
import java.nio.channels.*;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.locks.*;
import java.nio.ByteBuffer;

public class Assignment3 {

    static class StringUtils {

        public static int countMatches(String str, String kw) {
            int foundIndex = 0;
            int count = 0;
            foundIndex = str.indexOf(kw);
            while (foundIndex >= 0) {
                count++;
                foundIndex++;
                foundIndex = str.indexOf(kw, foundIndex);

            }
            return count;
        }
    }

    // node class for storaging book information
    static class Node {
        String data;
        Node next, bookNext, nextFrequentSearch;

        public Node(String data) {
            this.data = data;
        }
    }

    // shared list for storing book data across threads
    static class SharedList {
        ArrayList<Node> bookHeaders = new ArrayList<>();
        Node tail;
        Node head;
        Lock lock = new ReentrantLock();

        // append new node to the shared list with lock
        public void append(Node node) {
            lock.lock();
            try {
                if (head == null) {
                    head = node;
                }

                if (tail != null) {
                    tail.next = node;
                }
                tail = node;
            } finally {
                lock.unlock();
            }
        }

        public void appendBookHeader(Node bookHeader) {
            lock.lock();
            this.bookHeaders.add(bookHeader);
            lock.unlock();
        }

        public void setNextFrequentSearchNode(String searchKeyword) {
            lock.lock();
            for (Node n : this.bookHeaders) {
                Node curNode = n.bookNext;
                Node curSearchNode = n;
                while (curNode != null) {
                    if (StringUtils.countMatches(curNode.data, searchKeyword) > 0) {
                        curSearchNode.nextFrequentSearch = curNode;
                        curSearchNode = curNode;
                    }
                    curNode = curNode.bookNext;
                }
            }
            lock.unlock();
        }

        public ArrayList<Node> getBookHeaders() {
            return this.bookHeaders;
        }
    }

    // to manage individual client connections
    static class Client implements Runnable {
        SocketChannel clientChannel;
        Selector selector;
        SharedList sharedList;

        Node head;
        Node tail;

        int bookNumber;
        long lastReceivedTime;

        public Client(SocketChannel clientChannel, SharedList sharedList, int bookNumber) throws IOException {
            this.clientChannel = clientChannel;
            this.sharedList = sharedList;
            this.bookNumber = bookNumber;

            this.clientChannel.configureBlocking(false);

            this.selector = Selector.open();

            this.clientChannel.register(selector, SelectionKey.OP_READ).attach(new Runnable() {

                @Override
                public void run() {
                    read();
                }
            });

            this.lastReceivedTime = System.currentTimeMillis();
        }

        // Method to read data from the client
        public void read() {
            ByteBuffer buffer = ByteBuffer.allocate(1024);
            try {
                int bytesRead = clientChannel.read(buffer);
                if (bytesRead > 0) {
                    lastReceivedTime = System.currentTimeMillis();
                    buffer.flip();

                    String decodedData = new String(buffer.array(), 0, bytesRead);
                    Node node = new Node(decodedData);

                    if (head == null) {
                        head = node;
                        this.sharedList.appendBookHeader(head);
                    } else {
                        tail.bookNext = node;
                    }

                    tail = node;
                    sharedList.append(node);

                } else if (bytesRead == -1) {

                    selector.keys().forEach(SelectionKey::cancel);

                    clientChannel.close();

                    save();
                }
            } catch (IOException e) {

                try {
                    selector.keys().forEach(SelectionKey::cancel);

                    clientChannel.close();
                } catch (IOException ignored) {
                }
            }
        }

        // method to save the book data as a file
        public void save() {
            String filename = "book_" + String.format("%02d", bookNumber) + ".txt";
            try (PrintWriter out = new PrintWriter(filename, "UTF-8")) {
                Node current = head;
                while (current != null) {
                    out.write(current.data);
                    current = current.bookNext;
                }
                System.out.println("Save book_" + String.format("%02d", bookNumber) + " to " + filename);
            } catch (FileNotFoundException | UnsupportedEncodingException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void run() {
            while (true) {
                try {
                    int readyChannels = selector.select(1000); // timeout set to 1 second

                    if (readyChannels > 0) {
                        Set<SelectionKey> keys = selector.selectedKeys();
                        Iterator<SelectionKey> keyIterator = keys.iterator();

                        while (keyIterator.hasNext()) {
                            SelectionKey key = keyIterator.next();
                            Runnable runnable = (Runnable) key.attachment();

                            runnable.run();
                            keyIterator.remove();
                        }
                    }
                    // if no data received, connection close
                    if (System.currentTimeMillis() - lastReceivedTime > 5000) {

                        selector.keys().forEach(SelectionKey::cancel);
                        clientChannel.close();

                        save();
                        break;
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    // Server class
    static class EchoServer {
        ServerSocketChannel serverChannel;
        int bookCount = 0;
        SharedList sharedList;
        String searchKeyword;

        public EchoServer(String host, int port, String searchKeyword) throws IOException {
            this.searchKeyword = searchKeyword;
            this.sharedList = new SharedList();
            bookCount = 0;
            serverChannel = ServerSocketChannel.open();
            serverChannel.bind(new InetSocketAddress(host, port));
        }

        // keep accepting from clients to connect
        public void connectToClient() {
            while (true) {

                try {
                    SocketChannel clientChannel = serverChannel.accept();
                    // System.out.println("Connect to: " + clientChannel.getRemoteAddress());
                    bookCount++;
                    // System.out.println(String.format("Current bookCount=%d", bookCount));

                    Client client = new Client(clientChannel, sharedList, bookCount);
                    new Thread(client).start();

                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        public void setNextFreqSearchPointer() {
            while (true) {
                try {
                    // Execute every 4 seconds
                    Thread.sleep(4000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                this.sharedList.setNextFrequentSearchNode(this.searchKeyword);
            }
        }

        public void analyseSearch() {
            while (true) {
                try {
                    // Execute every 5 seconds
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                ArrayList<Node> bookHeads = this.sharedList.getBookHeaders();
                HashMap<String, Integer> searchAnalysisList = new HashMap<String, Integer>();

                for (Node n : bookHeads) {
                    String bookTitle = this.getBookTitle(n);
                    Integer searchCount = 0;
                    Node curSearchNode = n;
                    while (curSearchNode != null) {
                        searchCount += StringUtils.countMatches(curSearchNode.data, this.searchKeyword);
                        // System.out.println("- - - - - ");
                        // System.out.println(curSearchNode.data);
                        // System.out.println("- - - - - ");

                        curSearchNode = curSearchNode.nextFrequentSearch;
                    }

                    searchAnalysisList.put(bookTitle, searchCount);
                    // System.out.println(String.format(".Added new book <%s> with <%s> search
                    // occurrences.", bookTitle, searchCount));
                }

                HashMap<String, Integer> sortedSearchAnalysisList = this.sortByValue(searchAnalysisList);
                this.printCurrentDateTime();
                for (Map.Entry<String, Integer> a : sortedSearchAnalysisList.entrySet()) {
                    System.out.println(String.format("+ Book <%s> contains <%s> occurrences of search keyword=<%s>",
                            a.getKey(), a.getValue(), this.searchKeyword));
                }
                System.out.println();
            }
        }

        private void printCurrentDateTime() {
            DateTimeFormatter DEFAULT_DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                    .withZone(ZoneId.systemDefault());
            LocalDateTime date = LocalDateTime.now();
            String dateTimeText = date.format(DEFAULT_DATETIME_FORMATTER);
            System.out.println(String.format("[ %s ]", dateTimeText));
        }

        private HashMap<String, Integer> sortByValue(HashMap<String, Integer> hm) {
            // Create a list from elements of HashMap
            List<Map.Entry<String, Integer>> list = new LinkedList<Map.Entry<String, Integer>>(
                    hm.entrySet());

            // Sort the list using lambda expression
            Collections.sort(
                    list,
                    (i1,
                            i2) -> i2.getValue().compareTo(i1.getValue()));

            // put data from sorted list to hashmap
            HashMap<String, Integer> temp = new LinkedHashMap<String, Integer>();
            for (Map.Entry<String, Integer> aa : list) {
                temp.put(aa.getKey(), aa.getValue());
            }
            return temp;
        }

        private String getBookTitle(Node bookHead) {
            if (bookHead == null)
                return "";

            return bookHead.data.split("\r\n")[0];
        }

        // start server
        public void run() {
            Thread acceptThread = new Thread(this::connectToClient);
            acceptThread.start();

            Thread setNextFreqSearchPointer = new Thread(this::setNextFreqSearchPointer);
            setNextFreqSearchPointer.start();

            Thread analyseSearch = new Thread(this::analyseSearch);
            analyseSearch.start();

            try {
                acceptThread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    public static void main(String[] args) {

        // get port number
        int port = 12345; // Default port
        String searchKeyword = "happy"; // default searchKeyword

        if (args.length == 2) {
            port = Integer.parseInt(args[1]);
        } else if (args.length == 4) {
            port = Integer.parseInt(args[1]);
            searchKeyword = args[3];
        }

        searchKeyword = searchKeyword.replaceAll("\"", "");

        // run server
        try {
            EchoServer server = new EchoServer("localhost", port, searchKeyword);
            server.run();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}