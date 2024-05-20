# Service_Client
## Introduction
This repository contains the implementation of an extended Trivial File Transfer Protocol (TFTP) server and client. The TFTP server facilitates file transfers between multiple users, allowing them to upload and download files while also announcing when files are added or deleted to the server. Communication between the server and clients is performed using a binary communication protocol supporting upload, download, and file lookup functionalities.

## Server Implementation
The server implementation is based on the Thread-Per-Client (TPC) server pattern. Upon receiving a message from a client, the server can reply back to the client. Additionally, the server is capable of sending messages between clients or broadcasting announcements to all clients.

## Client Behavior and Commands
The client utilizes two threads: one for reading input from the user keyboard and the other for reading input from the socket (referred to as the listening thread). The keyboard thread interprets commands entered by the user and sends corresponding packets to the server, while the listening thread handles incoming packets from the server.

### Keyboard Thread Commands
1. **LOGRQ**
   - Login user to the server.
   - Format: `LOGRQ <Username>`
   - Example: `LOGRQ KELVE YAM`

2. **DELRQ**
   - Delete file from the server.
   - Format: `DELRQ <Filename>`
   - Example: `DELRQ lehem hvita`

3. **RRQ**
   - Download file from the server's Files folder to the current working directory.
   - Format: `RRQ <Filename>`
   - Example: `RRQ kelve yam.mp3`

4. **WRQ**
   - Upload file from the current working directory to the server.
   - Format: `WRQ <Filename>`
   - Example: `WRQ Operation Grandma.mp4`

5. **DIRQ**
   - List all filenames in the server's Files folder.
   - Format: `DIRQ`

6. **DISC**
   - Disconnect from the server and close the program.
   - Format: `DISC`

### Listening Thread Behavior
- **DATA Packet:** Save data to a file or buffer depending on the command and send an ACK packet in return.
- **ACK Packet:** Print `ACK <block number>` to the terminal.
- **BCAST Packet:** Print `BCAST <del/add> <file name>` to the terminal.
- **Error Packet:** Print `Error <Error number> <Error Message if exist>` to the terminal.

**For further details, please refer to the assignment document.**

## Getting Started

Clone this repository to your local machine. Go to main for both client and server. start both of them.

## Contributing

This project was developed by Bar Zuckerman and Yarden Levi.

## License

This project is licensed as homework for BGU. All rights reserved.

