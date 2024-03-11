package bgu.spl.net.impl.tftp;

import bgu.spl.net.impl.srv.BaseClient;

import java.io.*;
import java.net.Socket;
import java.util.Arrays;
import java.util.Scanner;
import java.util.concurrent.ConcurrentLinkedQueue;

public class TftpClient {

    public static void main(String[] args) throws IOException {
        if (args.length == 0) {
            args = new String[]{"localhost"};
        }

        if (args.length >= 2) {
            System.out.println("you must supply one argument: host");
            System.exit(1);
        }
        BaseClient baseClient = new BaseClient(7777,
                new TftpClientProtocol(), new TftpEncoderDecoder());
        baseClient.consume(args[0]);
    }
}
