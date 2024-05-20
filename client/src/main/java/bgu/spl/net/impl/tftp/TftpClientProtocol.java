package bgu.spl.net.impl.tftp;

import bgu.spl.net.api.MessagingProtocol;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;

public class TftpClientProtocol implements MessagingProtocol<byte[]> {
    final int MAX_DATA_SIZE = 512;
    private ConcurrentHashMap<Short, byte[]> blocksMap = new ConcurrentHashMap<>();
    private boolean isRRQ = false;
    private boolean shouldTerminate = false;
    public final Object lock = new Object();
    private boolean errorRecieve = false;
    volatile boolean isWaitingForServer = true;


    @Override
    public byte[] process(byte[] msg) {
        byte[] opcodeBytes = new byte[]{msg[0], msg[1]};
        short opcodeShort = byteToShort(opcodeBytes); //we transform the opcode into short, so we can handel it in switch case
        byte [] msgToDisplay;
        switch (opcodeShort) {
            case 05: // ERROR
                errorRecieve = true;
                byte[] errorNum = new byte[]{msg[2], msg[3]};
                String errorMsg = new String(msg, 4, msg.length-4, StandardCharsets.UTF_8);
                System.out.println("Error " + (int)byteToShort(errorNum) + " " + errorMsg);

                synchronized (lock) {
                    isWaitingForServer = false;
                    lock.notifyAll();
                }
                return null;
            case 03: // DATA
                return dataPacketRecieve(msg); // return ack according to the block
            case 04: // ACK
                errorRecieve = false;
                byte[] blockNum = new byte[]{msg[2], msg[3]};
                System.out.println("ACK " + (int)byteToShort(blockNum));
                if ((int)byteToShort(blockNum) == 0) {
                    synchronized (lock) {
                        isWaitingForServer = false;
                        lock.notifyAll();
                    }
                }
                return null;
            case 9: // BCAST
                String isDeleted = new String(msg, 2, 1, StandardCharsets.UTF_8);
                String isDeletesString ="";
                String fileName = new String(msg, 3, msg.length - 3, StandardCharsets.UTF_8);
                if(isDeleted.equals("1"))
                    isDeletesString =  "del";
                else {
                    isDeletesString = "add";
                }
                System.out.println("BCAST " + isDeletesString + " " + fileName);
                return null;
        }
        return null;
    }

    @Override
    public boolean shouldTerminate() {
        return shouldTerminate;
    }
    public void setShouldTerminate(boolean value){
        shouldTerminate = value;
    }

    private byte[] dataPacketRecieve (byte[] msg){
        byte[] packetSize = new byte[2];
        System.arraycopy(msg, 2, packetSize, 0, 2);
        byte[] block = new byte[2];
        System.arraycopy(msg, 4, block, 0, 2);
        byte[] data = new byte[msg.length - 6];
        System.arraycopy(msg, 6, data, 0, msg.length - 6);
        short blockAsShort = byteToShort(block);
        blocksMap.put(blockAsShort, data); // insert the current block into the map no matter which block I am

        if (byteToShort(packetSize) < MAX_DATA_SIZE) { // check if it is the last block to be received
            if (isRRQ) { // we are in RRQ Command
                try {
                    byte[] nameByte = blocksMap.get((short) 0);
                    String name = new String(nameByte, 0, nameByte.length, StandardCharsets.UTF_8);
                    FileOutputStream fileOutputStream = new FileOutputStream("./" + name, true);
                    blocksMap.remove((short) 0);
                    for (short blockNum : blocksMap.keySet()) { // iterate each block and writing it into the file
                        fileOutputStream.write(blocksMap.get(blockNum));
                        blocksMap.remove(blockNum);
                    }
                    fileOutputStream.close();
                    System.out.println("RRQ " + name + " complete");
                } catch (IOException e) {
                }
            }
            else { // we are in DIRQ Command
                byte[] buffer = new byte[(int) (blockAsShort-1)*512+byteToShort(packetSize)];
                int offset = 0;
                for (short blockNum : blocksMap.keySet()) {
                    byte[] blockContent = blocksMap.get(blockNum);
                    System.arraycopy(blockContent, 0, buffer, offset, blockContent.length);
                    offset += blockContent.length;
                }
                String list = new String(buffer, 0, buffer.length, StandardCharsets.UTF_8);
                String[] namesOfFiles = list.split("0");

                for (String name :namesOfFiles){
                    System.out.println(name);
                }
            }
            synchronized (lock) {
                isWaitingForServer = false;
                lock.notifyAll(); // after receiving the last block
            }
        }
        return acknowledgmentSend(blockAsShort); // send ack according to the current block
    }

    private byte [] acknowledgmentSend(short blockNum) {
        short opcode = 04;
        byte[] ack = new byte[4];

        byte[] opcodeBytes = shortToBytes(opcode);
        byte[] blockNumBytes = shortToBytes(blockNum);

        System.arraycopy(opcodeBytes, 0, ack, 0, 2);
        System.arraycopy(blockNumBytes, 0, ack, 2, 2);

        return ack;
    }

    private short byteToShort(byte[] bytes) {
        return (short) (((short) bytes[0]) << 8 | (short) (bytes[1] & 0x00ff));
    }

    private byte[] shortToBytes(short a) {
        byte[] a_bytes = new byte[]{(byte) (a >> 8), (byte) (a & 0xff)};
        return a_bytes;
    }
    public boolean getRRQ(){
        return isRRQ;
    }
    public boolean errorRecieved(){
        return errorRecieve;
    }
    public void setRRQ(boolean change){
        isRRQ = change;
    }

    public void setBlocksMap(short key , byte[] value){
        blocksMap.put(key, value);
    }

}
