package bgu.spl.net.impl.srv;

import bgu.spl.net.impl.Util;
import bgu.spl.net.impl.tftp.TftpClientProtocol;
import bgu.spl.net.impl.tftp.TftpEncoderDecoder;


import java.io.*;
import java.net.Socket;
import java.util.Arrays;
import java.util.Scanner;

public class BaseClient {
    private final int port;
    private final TftpClientProtocol protocol;
    private final TftpEncoderDecoder encdec;
    private Socket sock;
    boolean terminate;
    private final Scanner scanner;
    private Thread listeningThread;
    private Thread keyboardTread;
    private BufferedInputStream in;
    private BufferedOutputStream out;


    public BaseClient(
            int port,
            TftpClientProtocol protocol,
            TftpEncoderDecoder encdec) {
        this.port = port;
        this.protocol = protocol;
        this.encdec = encdec;
        this.terminate = false;
        this.sock = null;
        this.listeningThread = null;
        this.keyboardTread = null;
        this.scanner = new Scanner(System.in);
    }

    public void consume(String host) {
        //BufferedReader and BufferedWriter automatically using UTF-8 encoding
        try {
            sock = new Socket(host, port);
            this.in = new BufferedInputStream(new BufferedInputStream(sock.getInputStream()));
            this.out = new BufferedOutputStream(new BufferedOutputStream(sock.getOutputStream()));
            keyboardTread = Thread.currentThread();
            listeningThread = new Thread(listen(), "listening thread");
            listeningThread.start();
            while (!terminate & !protocol.shouldTerminate()) {
                System.out.println("please type message to the server");
                boolean correctInput = false;
                byte[] packet = null;
                while (!correctInput & !terminate) {
                    String order = scanner.nextLine();
                    protocol.recievedAnswer = false;
                    try {
                        packet = encdec.encode(order);
                        correctInput = true;
                    } catch (IllegalArgumentException e) {
                        correctInput = false;
                        System.out.println("wrong input");
                    }
                }
                if (packet != null) {
                    Util.OP request = Util.getOpByByte(packet[1]);
                    protocol.inform(request);
                    if (request == Util.OP.RRQ) {
                        protocol.inform(new String(
                                Arrays.copyOfRange(packet, 2, packet.length - 1)));
                    }
                    send(packet);
                }
                synchronized (this) {
                    try {
                        while (!protocol.recievedAnswer) this.wait();
                    } catch (InterruptedException ignored) {
                    }
                }
            }
            listeningThread.interrupt();
            listeningThread.join();
            sock.close();
            System.out.println("client terminated");
        } catch (Exception ex) {
        }
    }

    private Runnable listen() {
        return () -> {
            System.out.println("started listening");
            int read;
            try {
                while (!protocol.shouldTerminate() && !terminate && (read = in.read()) >= 0) {

                    byte nextByte = (byte) read;
                    byte[] answer = encdec.decodeNextByte(nextByte);
                    if (answer == null) continue;
                    byte[] result = protocol.process(answer);
                    if (result != null) send(result);
                    if (protocol.recievedAnswer) {
                        synchronized (this) {
                            this.notifyAll();
                        }
                    }
                }
            } catch (IOException e) {
                terminate = true;
                System.out.println("server probably down");
                protocol.recievedAnswer = true;
                keyboardTread.interrupt();
            }
            System.out.println("listening thread is terminated");
        };
    }

    private synchronized void send(byte[] packet) throws IOException {
        out.write(packet);
        out.flush();
    }
}
