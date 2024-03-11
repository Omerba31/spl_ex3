package bgu.spl.net.impl.srv;

import bgu.spl.net.impl.tftp.TftpClientProtocol;
import bgu.spl.net.impl.tftp.TftpEncoderDecoder;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.Arrays;
import java.util.Scanner;
import java.util.concurrent.ConcurrentLinkedQueue;

public class BaseClient {
    private final int port;
    private final TftpClientProtocol protocol;
    private final TftpEncoderDecoder encdec;
    private Socket sock;
    boolean terminate = false;
    private Scanner scanner;
    ConcurrentLinkedQueue missionsQueue;

    private Thread keyBoardThread;
    private Thread listeningThread;

    public BaseClient(
            int port,
            TftpClientProtocol protocol,
            TftpEncoderDecoder encdec) {
        this.port = port;
        this.protocol = protocol;
        this.encdec = encdec;
        this.sock = null;
        this.listeningThread = null;
        this.missionsQueue = new ConcurrentLinkedQueue<>();
        this.scanner = new Scanner(System.in);
    }

    public void consume(String host) {
        keyBoardThread = Thread.currentThread();
        //BufferedReader and BufferedWriter automatically using UTF-8 encoding
        try (Socket sock = new Socket(host, port);
             BufferedInputStream in = new BufferedInputStream(
                     new BufferedInputStream(sock.getInputStream()));
             BufferedOutputStream out = new BufferedOutputStream(
                     new BufferedOutputStream(sock.getOutputStream()))) {

            while (!terminate) {
                System.out.println("please type message to the server");
                out.write(encdec.encode(scanner.nextLine().getBytes()));
                out.flush();

                System.out.println("awaiting response");
                byte nextByte = (byte) in.read();
                byte[] answer = null;
                while (answer == null) answer = encdec.decodeNextByte(nextByte);
                System.out.println("message from server: " + Arrays.toString(answer));
            }
        } catch (IOException ex) {
        }

    }


}
