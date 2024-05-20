package bgu.spl.net.impl.tftp;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Scanner;

public class KeyboardThread implements Runnable {
    private final TftpClientProtocol protocol;
    private final TftpEncoderDecoder encdec;
    private final BufferedOutputStream out;
    final int MAX_DATA_SIZE = 512;

    public KeyboardThread(TftpEncoderDecoder reader, TftpClientProtocol protocol, BufferedOutputStream out) {
        this.encdec = reader;
        this.protocol = protocol;
        this.out = out;
    }

    @Override
    public void run() {
        try (Scanner scanner = new Scanner(System.in)) {
            while (!protocol.shouldTerminate()) {
                String userInput = scanner.nextLine();
                packetMaker(userInput);
            }
        }
    }

    private void send(byte[] packetToSend) {
        try {
            if (packetToSend != null) {
                out.write(encdec.encode(packetToSend));
                out.flush();
            }
        }
        catch (IOException e) {
            System.out.println("problem");
        }
    }


    private void packetMaker(String input) {
        protocol.isWaitingForServer = true; // a flag for when the client needs to receive a response from the server
        String[] inputArray = input.split("\\s+", 2);
        String request = inputArray[0];// split the command from the input
        String parameter = "";
        if (inputArray.length > 1)
            parameter = inputArray[1]; // split the parameter from the input

        short opcode = 0;
        switch (request) {
            case "DISC":
                opcode = 10;
                synchronized (protocol.lock) {
                    send(shortToBytes(opcode));
                    try {
                        while (protocol.isWaitingForServer) {
                            protocol.lock.wait();
                        }
                    } catch (InterruptedException e) {
                    }
                }
                if (!protocol.errorRecieved()) {
                    protocol.setShouldTerminate(true);
                }
                return;
            case "DIRQ":
                opcode = 6;
                protocol.setRRQ(false);
                synchronized (protocol.lock) {
                    send(shortToBytes(opcode));
                    try {
                        while (protocol.isWaitingForServer) {
                            protocol.lock.wait();
                        }
                    } catch (InterruptedException e) {
                    }
                }
                return;
            case "LOGRQ":
                opcode = 7;
                break;
            case "DELRQ":
                opcode = 8;
                break;
            case "RRQ":
                opcode = 1;
                if (checkFileExist(parameter)) {
                    System.out.println("file already exists");
                    return;
                }
                createFile(parameter);
                protocol.setRRQ(true);
                break;
            case "WRQ":
                opcode = 2;
                if (!checkFileExist(parameter)) {
                    System.out.println("file does not exists");
                    return;
                }
                break;
            default:
                System.out.println("INVALID COMMAND");
                return;
        }
        byte[] opcodeBytes = shortToBytes(opcode);// convert the opcode into byte[]
        byte[] parameterBytes = parameter.getBytes(StandardCharsets.UTF_8); // convert the parameter into byte[]

        byte[] packet = new byte[3 + parameterBytes.length]; // build the packet
        System.arraycopy(opcodeBytes, 0, packet, 0, 2);
        System.arraycopy(parameterBytes, 0, packet, 2, parameterBytes.length);
        System.arraycopy(shortToBytes((short) 0), 0, packet, parameterBytes.length + 2, 1); // 0 byte at the end
        if (request.equals("RRQ")) {
            synchronized (protocol.lock) {
                send(packet);
                try {
                    while (protocol.isWaitingForServer) {
                        protocol.lock.wait();
                    }
                } catch (InterruptedException e) {
                }

                if (protocol.errorRecieved()) {
                    File fileToDelete = new File("./" + parameter);
                    if (fileToDelete.exists() && fileToDelete.isFile())
                        fileToDelete.delete(); //deletes the real file
                }
            }
        }
        else {
            synchronized (protocol.lock) {
                send(packet);
                try {
                    while (protocol.isWaitingForServer) {
                        protocol.lock.wait();
                    }
                } catch (InterruptedException e) {
                }

                if (request.equals("WRQ") && !protocol.errorRecieved()) {
                    getFile(parameter);
                }
            }
        }
    }

    private boolean checkFileExist(String parameter) {
        String folderPath = "./";

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(Paths.get(folderPath))) {
            for (Path file : stream) {
                String currentFile = String.valueOf(file.getFileName());
                if (currentFile.startsWith(".nfs") && Files.isRegularFile(file) && currentFile.equals(parameter)) { //because when we delete a file it may create us a temporary file and we want to avoid sending it
                    return true;
                }
            }
        } catch (IOException e) {
            return false;
        }
        return false;
    }

    private void createFile(String fileName) {
        try {
            FileOutputStream fileOutputStream = new FileOutputStream("./" + fileName);
            protocol.setBlocksMap((short) 0, fileName.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
        }
    }

    private void getFile(String fileName) {
        String filePath = "./" + fileName;
        try (FileInputStream fis = new FileInputStream(filePath)) {
            long fileSize = fis.available();
            byte[] buffer = new byte[(int) fileSize];
            int bytesRead = fis.read(buffer);
            if (bytesRead == fileSize) {
                dataPacketSend(buffer);
            } else {
                System.out.println("Error: Unable to read the entire file into the buffer.");
            }
        } catch (IOException e) {
        }
    }

    private void dataPacketSend(byte[] message) {
        short opcode = 03;
        byte[] opcodeBytes = shortToBytes(opcode);
        double blockNumDouble = (double) message.length / MAX_DATA_SIZE;
        short blockNum = (short)Math.max( Math.ceil(blockNumDouble), 1); // in case the file is an empty file - block num is 1 and not 0
        short ackNum = 1;

        while (ackNum <= blockNum) {
            int packetSize = Math.min(message.length, MAX_DATA_SIZE);
            byte[] dataPacket = new byte[6 + packetSize];
            byte[] blockNumBytes = shortToBytes(ackNum);
            byte[] data = Arrays.copyOfRange(message, 0, packetSize);

            System.arraycopy(opcodeBytes, 0, dataPacket, 0, 2); //opcode for dataPacket
            System.arraycopy(shortToBytes((short) packetSize), 0, dataPacket, 2, 2); // size of the actual data
            System.arraycopy(blockNumBytes, 0, dataPacket, 4, 2); //block size
            System.arraycopy(data, 0, dataPacket, 6, packetSize); //the data itself

            if (ackNum < blockNum) // current block isn't the last block
                message = Arrays.copyOfRange(message, packetSize, message.length); //getting the sub array

            ackNum++;
            send(dataPacket);

        }

    }

    private short byteToShort(byte[] bytes) {
        return (short) (((short) bytes[0]) << 8 | (short) (bytes[1] & 0x00ff));
    }

    private byte[] shortToBytes(short a) {
        byte[] a_bytes = new byte[]{(byte) (a >> 8), (byte) (a & 0xff)};
        return a_bytes;
    }
}
