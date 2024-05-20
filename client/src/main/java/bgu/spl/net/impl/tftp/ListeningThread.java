package bgu.spl.net.impl.tftp;

import bgu.spl.net.api.MessageEncoderDecoder;
import bgu.spl.net.api.MessagingProtocol;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.IOException;


public class ListeningThread<T> implements Runnable{
    private final MessagingProtocol<T> protocol;
    private final MessageEncoderDecoder<T> encdec;
    private BufferedInputStream in;
    private BufferedOutputStream out;

    public ListeningThread(MessageEncoderDecoder<T> reader, MessagingProtocol<T> protocol , BufferedInputStream in, BufferedOutputStream out){
        this.encdec = reader;
        this.protocol = protocol;
        this.in = in;
        this.out = out;
    }
    @Override
    public void run() {
        try {
            while (!protocol.shouldTerminate()) {
                int nextByte = in.read();
                if (nextByte >= 0) {
                    T message = encdec.decodeNextByte((byte) nextByte);
                    if (message != null) {
                        T msg = protocol.process(message);
                        send(msg);
                    }
                }
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    private void send (T packetToSend){
        try {
            if (packetToSend != null) {
                out.write(encdec.encode(packetToSend));
                out.flush();
            }
        } catch (IOException e) {
        }
    }
}
