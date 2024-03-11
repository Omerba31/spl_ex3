package bgu.spl.net.impl.srv;

import bgu.spl.net.impl.Util;
import bgu.spl.net.impl.tftp.TftpClientProtocol;
import bgu.spl.net.impl.tftp.TftpEncoderDecoder;
import bgu.spl.net.api.MessagingProtocol;
import bgu.spl.net.api.MessageEncoderDecoder;


import java.io.*;
import java.net.Socket;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.ConcurrentLinkedQueue;

public class BaseClient {
    private final int port;
    private final TftpClientProtocol protocol;
    private final TftpEncoderDecoder encdec;
    private Socket sock;
    boolean terminate;
    private Scanner scanner;
    private Thread listeningThread;
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
        this.scanner = new Scanner(System.in);
    }

    public void consume(String host) {
        //BufferedReader and BufferedWriter automatically using UTF-8 encoding
        try {
            sock = new Socket(host, port);
            this.in = new BufferedInputStream(new BufferedInputStream(sock.getInputStream()));
            this.out = new BufferedOutputStream(new BufferedOutputStream(sock.getOutputStream()));
            listeningThread = new Thread(listen());
            listeningThread.start();
            while (!terminate) {
                System.out.println("please type message to the server");
                boolean correctInput = false;
                byte[] packet = null;
                while (!correctInput) {
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
                switch (packet[1]) {
                    case 0xa: //Disc
                        terminate = true;
                        continue;
                    case 3: //WRQ
                        protocol.request = TftpClientProtocol.Request.RRQ;
                    case 6: //dirQ
                        protocol.request = TftpClientProtocol.Request.DIRQ;
                }
                if (packet != null) {
                    send(packet);
                }
                synchronized (this) {
                    try {
                        while (!protocol.recievedAnswer) this.wait();
                    } catch (InterruptedException ignored) {
                    }
                }
            }
            listeningThread.join();
            sock.close();
            System.out.println("client terminated");
        } catch (Exception ex) {
        }
    }

    private Runnable listen() {
        return () -> {
            System.out.println("started listening");
            int read=0;
            try {
                while (!protocol.shouldTerminate() && (read = in.read()) >= 0) {
                    // doesn't take the first 0 todo
                    byte nextByte = (byte) read;
                    byte[] answer = encdec.decodeNextByte(nextByte);
                    if (answer==null) continue;
                    byte[] result = protocol.process(answer);
                    if (result != null) send(result);
                    if (protocol.recievedAnswer) {
                        synchronized (this) {
                            this.notifyAll();
                        }
                    }
                    System.out.println("message from server: " + Arrays.toString(answer));
                }
            }catch(IOException e){
                terminate = true;
            }
            System.out.println("listening thread is terminated");
        };
    }

    private synchronized void send(byte[] packet) throws IOException {
        out.write(packet);
        out.flush();
    }
}
