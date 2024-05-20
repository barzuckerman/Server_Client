package bgu.spl.net.impl.tftp;

import java.io.*;
import java.net.Socket;
import java.net.UnknownHostException;

public class TftpClient {
    //TODO: implement the main logic of the client, when using a thread per client the main logic goes here
    public static void main(String[] args) {
        String host = args[0];
        int port = Integer.parseInt(args[1]);
        try (Socket sock = new Socket(host, port);
             BufferedInputStream in = new BufferedInputStream(sock.getInputStream());
             BufferedOutputStream out = new BufferedOutputStream(sock.getOutputStream())) {
            TftpClientProtocol protocol = new TftpClientProtocol();
            TftpEncoderDecoder encoderDecoder = new TftpEncoderDecoder();
            Thread keyboard = new Thread(new KeyboardThread(encoderDecoder, protocol, out));
            Thread listening = new Thread(new ListeningThread<>(encoderDecoder, protocol, in, out));
            System.out.println("client connected");
            keyboard.start();
            listening.start();
            keyboard.join();
            listening.join();

        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
