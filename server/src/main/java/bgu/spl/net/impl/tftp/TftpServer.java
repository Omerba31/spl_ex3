package bgu.spl.net.impl.tftp;

import bgu.spl.net.srv.Server;

public class TftpServer {
    public static void main(String[] args) {

        // you can use any server...
        Server.threadPerClient(
                7777, //port
                () -> new TftpProtocol(0, new TftpConnections()), //protocol factory
                TftpEncoderDecoder::new //message encoder decoder factory
        ).serve();
    }
}
