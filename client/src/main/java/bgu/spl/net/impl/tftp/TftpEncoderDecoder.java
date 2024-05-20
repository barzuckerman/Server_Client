package bgu.spl.net.impl.tftp;
import bgu.spl.net.api.MessageEncoderDecoder;
import java.util.Arrays;

public class TftpEncoderDecoder implements MessageEncoderDecoder<byte[]> {
    //TODO: Implement here the TFTP encoder and decoder
    private byte[] bytes = new byte[1 << 10]; //start with 1k
    private int len = 0;

    @Override
    public byte[] decodeNextByte(byte nextByte) {
        // TODO: implement this
        // for DIRQ, DISC
        if (len == 1 && (nextByte == 10 || nextByte == 6)) {
            return handel(nextByte);
        }

        // for ACK
        if (len == 3 && bytes[1] == 4) {
            return handel(nextByte);
        }

        // for DATA
        if (len >= 5 && bytes[1] == 3) {
            short lenPackage = (short) (((short) bytes[2]) << 8 | (short) (bytes[3] & 0x00ff));
            if (len - 5 == lenPackage)
                return handel(nextByte);
        }

        // for RRQ, WRQ, ERROR, LOGRQ, DELRQ, BCAST
        if (len >= 3 && nextByte == 0x0000 && bytes[1] != 3) { // when next byte is the end of the packet, we dont push the 0 byte
            byte[] result = Arrays.copyOf(bytes, len);
            bytes = new byte[1 << 10];
            len = 0;
            return result;
        }
        pushByte(nextByte);
        return null;
    }

    @Override
    public byte[] encode(byte[] message) {
        //TODO: implement this
        return message;
    }

    private void pushByte(byte nextByte) {
        if (len >= bytes.length) {
            bytes = Arrays.copyOf(bytes, len * 2);
        }
        bytes[len++] = nextByte;
    }

    private byte[] handel(byte nextByte) { // to handle end of packet
        pushByte(nextByte);
        byte[] result = Arrays.copyOf(bytes, len);
        bytes = new byte[1 << 10];
        len = 0;
        return result;
    }
}