package bgu.spl.net.impl.tftp;

import bgu.spl.net.srv.Server;

public class TftpServer {
    public static void main(String[] args) {

        // you can use any server...
        Server.threadPerClient(
                Integer.parseInt(args[0]), //port
                //() -> new TftpProtocol(0, new TftpConnections()), //protocol factory
                TftpProtocol::new,
                TftpEncoderDecoder::new //message encoder decoder factory
        ).serve();
        
    }
}
