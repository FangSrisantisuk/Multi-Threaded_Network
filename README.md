# Overview 
This server allows for the analysis of text data and search pattern occurrences within the received data.

# Server Program Setup:

## Download Textbook Files

- Download plain text format books (UTF-8) from the Gutenberg Project or other resources and save them locally.

## Compile Server Program

- Open a terminal in the directory containing Assignment3.java.

- Compile the server program using the following command:

```
javac Assignment3.java
```

## Run Server

- Start the server with the specified port and search pattern:

```
java Assignment3 -l 12345 -p "happy"
```

Replace 12345 with the desired listening port and "happy" with the intended search pattern.


# Client Program (Sending Text Files)

- Open a separate terminal or command prompt window.

## Install Netcat

If not installed, use your system package manager (such as Homebrew on macOS) or download the binary for Windows.

For Linux, install via the package manager:

```
sudo apt-get update
sudo apt-get install netcat
```

## Send Text Files to Server

- Utilise nc (Netcat) to send the text file to the server. Use the command below:

```
nc localhost 12345 -i <delay> < file.txt
```

Replace <delay> with the intended delay time in seconds.

Ensure that file.txt corresponds to the actual file name of the text file with the desired content.
Ensure the port number match the server's configuration.

Note: The server will count how many times the specified string pattern appears in the book and create a list to navigate the lines containing it.

## Terminate Server

- Terminate the server by closing the terminal or using appropriate termination commands based on your system (such as Ctrl + C).