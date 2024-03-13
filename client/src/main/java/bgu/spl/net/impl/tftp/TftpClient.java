package bgu.spl.net.impl.tftp;

import bgu.spl.net.impl.Util;
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

        if (args.length == 1| args.length > 2) {
            System.out.println("you must supply two arguments: host and port");
            System.exit(1);
        }

        BaseClient baseClient = new BaseClient(Integer.parseInt(args[1]),
                new TftpClientProtocol(), new TftpEncoderDecoder());
        baseClient.consume(args[0]);
    }
}
