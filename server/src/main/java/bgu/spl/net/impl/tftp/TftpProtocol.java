package bgu.spl.net.impl.tftp;

import bgu.spl.net.api.BidiMessagingProtocol;
import bgu.spl.net.srv.Connections;
import bgu.spl.net.srv.userDetails;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;

class holder { // save ConnectionIds
    static ConcurrentHashMap<Integer, userDetails> ids_login = new ConcurrentHashMap<>();
    static ConcurrentHashMap<String, Boolean> files = new ConcurrentHashMap<>();
    static ArrayList<String> filesExists = new ArrayList();

    static {
        initializeFiles(); // happens when the class is created (loads all the files to the arraylist)
    }

    private static void initializeFiles() {
        File folder = new File("Files/");
        File[] listOfFiles = folder.listFiles();

        if (listOfFiles != null) {
            for (File file : listOfFiles) {
                if (file.isFile()) {
                    String fileName = file.getName();
                    //filesExists.add(fileName);
                    files.put(fileName, true); // true: access allowed, false: access prohibited
                }
            }
        }
    }
}

public class TftpProtocol implements BidiMessagingProtocol<byte[]> {
    private boolean shouldTerminate = false;
    private int connectionId;
    private Connections<byte[]> connections;
    userDetails user;
    final short success = 0;
    String[] ErrorMsg = new String[]{"Not defined, see error message (if any).", "File not found – RRQ DELRQ of non-existing file", "Access violation – File cannot be written, read or deleted.0", "Disk full or allocation exceeded – No room in disk.", "Illegal TFTP operation – Unknown Opcode.", "File already exists – File name exists on WRQ.", " User not logged in – Any opcode received before Login completes.", "User already logged in – Login username already connected."};
    final int MAX_DATA_SIZE = 512;
    private ConcurrentHashMap<Short, byte[]> blocksMap = new ConcurrentHashMap<>();

    @Override
    public void start(int connectionId, Connections<byte[]> connections) {
        // TODO implement this
        this.shouldTerminate = false;
        this.connectionId = connectionId;
        this.connections = connections;
        this.user = new userDetails(connectionId);
        holder.ids_login.put(connectionId, user);
    }

    @Override
    public void process(byte[] message) {
        // TODO implement this
        byte[] opcodeBytes = new byte[]{message[0], message[1]};
        short opcodeShort = byteToShort(opcodeBytes); //we transform the opcode into short, so we can handel it in switch case
        switch (opcodeShort) {
            case 01: // RRQ
                readRequest(message);
                break;
            case 02: // WRQ
                writeRequest(message);
                break;
            case 03: // DATA
                dataPacket(message);
                break;
            case 04: // ACK
                acknowledgment(message);
                break;
            case 06: // DIRQ
                directoryListingRequest();
                break;
            case 07: // LOGRQ
                loginRequest(message);
                break;
            case 8: // DELRQ
                deleteFileRequest(message);
                break;
            case 10: // DISC
                disconnect();
                break;
            default:
                short er = 4;
                errorSend(er);
                break;

        }

    }

    @Override
    public boolean shouldTerminate() {
        // TODO implement this
        return shouldTerminate;
    }

    private void readRequest(byte[] message) {
        if (checkLogin()) {
            String fileToSend = new String(message, 2, message.length - 2, StandardCharsets.UTF_8); // subtract the username from the message
//            if (holder.filesExists.contains(fileToSend)) {
//                readFileContent(fileToSend);
//                return;
//            }
            if (holder.files.containsKey(fileToSend)) { // file exist in server
                if (checkAccessViolation(fileToSend)) { // file access is allowed (true)
                    holder.files.replace(fileToSend, false); // change to false because this file is about to be read 
                    readFileContent(fileToSend);
                    holder.files.replace(fileToSend, true);
                    return;
                }
            } else { // file not exist in server
                short er = 1;
                errorSend(er);
            }
        }
    }

    private void readFileContent(String filename) {
        String filePath = "Files/" + filename;
        try (FileInputStream fis = new FileInputStream(filePath)) {
            long fileSize = fis.available();
            byte[] buffer = new byte[(int) fileSize];
            int bytesRead = fis.read(buffer);
            if (bytesRead == fileSize) {
                dataPacketSend(buffer);
            } else {
                System.out.println("Error: Unable to read the entire file into the buffer.");
            }
            return;
        } catch (IOException e) {
        }
    }


    private void writeRequest(byte[] message) {
        if (checkLogin()) {
            String newFileName = new String(message, 2, message.length - 2, StandardCharsets.UTF_8); // subtract the username from the message
            if (!holder.files.containsKey(newFileName)) { // file not exist in server
                short blockAsShort = 0;
                blocksMap.put(blockAsShort, newFileName.getBytes(StandardCharsets.UTF_8)); // insert the file name to block 0
                acknowledgmentSend(success);
                short del = 0;
            }
            else { // file already exist in server
                short er = 5;
                errorSend(er);
                return;
            }
        }
    }

    private void dataPacket(byte[] message) {
        if (checkLogin()) {

            byte[] packetSize = new byte[2];
            System.arraycopy(message, 2, packetSize, 0, 2);
            byte[] block = new byte[2];
            System.arraycopy(message, 4, block, 0, 2);
            byte[] data = new byte[message.length - 6];
            System.arraycopy(message, 6, data, 0, message.length - 6);
            short blockAsShort = byteToShort(block);
            blocksMap.put(blockAsShort, data); // insert the current block into the map no matter which block I am
            acknowledgmentSend(blockAsShort); // send ack according to the current block

            if (byteToShort(packetSize) < MAX_DATA_SIZE) { // check if it is the last block to be received
                try {
                    byte[] nameByte = blocksMap.get((short) 0);
                    String name = new String(nameByte, 0, nameByte.length, StandardCharsets.UTF_8);
                    FileOutputStream fileOutputStream = new FileOutputStream("Files/" + name);
                    blocksMap.remove((short) 0);
                    for (short blockNum : blocksMap.keySet()) { // iterate each block and writing it into the file
                        fileOutputStream.write(blocksMap.get(blockNum));
                        blocksMap.remove(blockNum);
                    }
                    fileOutputStream.close();
                    holder.files.put(name, true); // add the new file, access is allowed (true) because finished uploading 
                    broadcastFile(nameByte, "0");
                } catch (IOException e) {
                }
            }
        }
    }


    private void acknowledgment(byte[] message) {
        byte[] blockNum = new byte[]{message[2], message[3]};
        System.out.println("ACK " + (int)byteToShort(blockNum));
    }

    private void directoryListingRequest() {
        if (checkLogin()) {
            StringBuilder fileListString = new StringBuilder(); //we are using string builder so we can append each name we have with 0 so we get a full string with all the names
            for (String fileName : holder.files.keySet()) { // iterate the files and add the names to fileListString divided by "0" 
                if (checkAccessViolation(fileName)) // file access is allowed
                    fileListString.append(fileName).append("0");
            }
            byte[] files = fileListString.toString().getBytes(StandardCharsets.UTF_8);
            dataPacketSend(files);
        }
    }

    private void loginRequest(byte[] message) {
        if (!user.getLoggedIn()) {
            String username = new String(message, 2, message.length - 2, StandardCharsets.UTF_8); // subtract the username from the message
            for (Integer userId : holder.ids_login.keySet()) {
                if (holder.ids_login.get(userId).getUsername().equals(username)) { // check if a client with this username is already exists
                    errorSend((short) 7);
                    return;
                }
            }
            user.setLoggedIn(true);
            user.setUsername(username);

            acknowledgmentSend(success);
        } else {
            errorSend((short) 7);
        }
    }

    private void deleteFileRequest(byte[] message) {
        if (checkLogin()) {
            String fileNameToDelete = new String(message, 2, message.length - 2, StandardCharsets.UTF_8);
            if (holder.files.containsKey(fileNameToDelete)) {// file exist in server
                if (checkAccessViolation(fileNameToDelete)) { // file access is allowed (true) 
                    holder.files.replace(fileNameToDelete, false); // change to false because this file is about to be deleted 
                    File fileToDelete = new File("Files/" + fileNameToDelete);
                    if (fileToDelete.exists() && fileToDelete.isFile()) {
                        if (fileToDelete.delete()) { //deletes the real file
                            holder.files.remove(fileNameToDelete);
                            acknowledgmentSend(success);
                        }
                    }
                    broadcastFile(fileNameToDelete.getBytes(), "1");
                }
            } else { // file not exist in server
                errorSend((short) 1);
            }
        }
    }

    private void broadcastFile(byte[] message, String isDeleted) {
        byte[] Bcast = new byte[message.length + 4];

        System.arraycopy(shortToBytes((short) 9), 0, Bcast, 0, 2); // opcode
        System.arraycopy(isDeleted.getBytes(StandardCharsets.UTF_8), 0, Bcast, 2, 1); // is deleted
        System.arraycopy(message, 0, Bcast, 3, message.length); // filename
        System.arraycopy(shortToBytes((short) 0), 0, Bcast, message.length + 3, 1); // 0 byte at the end
        for (Integer user : holder.ids_login.keySet()) {
            connections.send(user, Bcast);
        }
    }

    private void disconnect() {
        if(checkLogin()){
            user.setLoggedIn(false);
            holder.ids_login.remove(this.connectionId);
            acknowledgmentSend(success);
            this.connections.disconnect(this.connectionId);
            shouldTerminate = true;
        }
        
        

    }

    private short byteToShort(byte[] bytes) {
        return (short) (((short) bytes[0]) << 8 | (short) (bytes[1] & 0x00ff));
    }

    private byte[] shortToBytes(short a) {
        byte[] a_bytes = new byte[]{(byte) (a >> 8), (byte) (a & 0xff)};
        return a_bytes;
    }

    private boolean checkLogin() {
        if (!user.getLoggedIn()) {
            short er = 6;
            errorSend(er);
            return false;
        }
        return true;
    }

    private boolean checkAccessViolation(String fileName) { // check if the current file can be written, read or deleted
        if (!holder.files.get(fileName)) { // false: access prohibited
            short er = 2;
            errorSend(er);
            return false;
        } else { // true: access allowed
            return true;
        }
    }

    private void acknowledgmentSend(short blockNum) {
        short opcode = 04;
        byte[] ack = new byte[4];

        byte[] opcodeBytes = shortToBytes(opcode);
        byte[] blockNumBytes = shortToBytes(blockNum);

        System.arraycopy(opcodeBytes, 0, ack, 0, 2);
        System.arraycopy(blockNumBytes, 0, ack, 2, 2);

        connections.send(connectionId, ack);
    }

    private void errorSend(short errorNum) {
        short opcode = 05;
        byte[] errorMsg = (ErrorMsg[errorNum]).getBytes(StandardCharsets.UTF_8);
        byte[] error = new byte[5 + errorMsg.length];

        byte[] opcodeBytes = shortToBytes(opcode);
        byte[] errorNumAsBytes = shortToBytes(errorNum);

        System.arraycopy(opcodeBytes, 0, error, 0, 2);
        System.arraycopy(errorNumAsBytes, 0, error, 2, 2);
        System.arraycopy(errorMsg, 0, error, 4, errorMsg.length);
        System.arraycopy(shortToBytes((short) 0), 0, error, errorMsg.length + 4, 1); // 0 byte at the end
        connections.send(connectionId, error);
    }

    private void dataPacketSend(byte[] message) {
        short opcode = 03;
        byte[] opcodeBytes = shortToBytes(opcode);
        double blockNumDouble = (double) message.length / MAX_DATA_SIZE;
        short blockNum = (short) Math.max(Math.ceil(blockNumDouble), 1); // in case the file is an empty file - block num is 1 and not 0
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
            connections.send(connectionId, dataPacket);
        }
    }
}
