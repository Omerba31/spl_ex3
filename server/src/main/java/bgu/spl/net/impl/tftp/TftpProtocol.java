package bgu.spl.net.impl.tftp;

import bgu.spl.net.api.BidiMessagingProtocol;
import bgu.spl.net.srv.BlockingConnectionHandler;
import bgu.spl.net.srv.Connections;

import java.io.*;
import java.util.LinkedList;

public class TftpProtocol implements BidiMessagingProtocol<byte[]> {

    private boolean shouldTerminate;
    private volatile TftpConnections<byte[]> connections;
    private int ownerId;
    private boolean isConnected;
    private File openFile;
    private BlockingConnectionHandler<byte[]> handler;


    @Override
    public void start(int connectionId, Connections<byte[]> connections, BlockingConnectionHandler<byte[]> handler) {
        this.connections = (TftpConnections<byte[]>) connections;
        this.shouldTerminate = false;
        this.ownerId = connectionId;
        this.isConnected = false;
        this.handler = handler;
        this.openFile = null;
    }

    @Override
    public byte[] process(byte[] message) throws Exception {
        byte type = message[1];
        // user has to connect once
        if (!isConnected & type != 7) return TftpServerUtils.getError(6);
        if (isConnected & type == 7) return TftpServerUtils.getError(7);
        switch (type) {
            case 1:
                return RRQ(message);
            case 2:
                return WRQ(message);
            case 3:
                return dataRQ(message);
            case 4:
                return AckRQ(message);
            //case 5: not receiving errors from client
            case 6:
                return dirRQ(message);
            case 7:
                return LogRQ(message);
            case 8:
                return delRQ(message);
            //case 9: not receiving bCast from client
            case 0xa:
                return discRQ(message);
            default:
                throw new UnsupportedOperationException("not to be used from client");
        }
    }

    @Override
    public boolean shouldTerminate() {
        return shouldTerminate;
    }

    private byte[] RRQ(byte[] massage) throws Exception {
        // in the definition of RRQ packet, the first two bytes are OP and the last one is 0
        File file = TftpServerUtils.getFile(new String(
                TftpServerUtils.getPartOfArray(massage, 2, massage.length - 2)));
        if (!file.exists()) return TftpServerUtils.getError(1);
        openFile = file;
        return TftpServerUtils.createDataPacket((short) 1, TftpServerUtils.readPartOfFile(openFile, (short) 1));
    }

    private byte[] WRQ(byte[] massage) throws IOException {
        // in the definition of WRQ packet, the first two bytes are OP and the last one is 0
        File file = TftpServerUtils.getFile(new String(
                TftpServerUtils.getPartOfArray(massage, 2, massage.length - 2)));
        try {
            if (!file.createNewFile())
                return TftpServerUtils.getError(5); //ERROR - FILE ALREADY EXISTS
            else {
                //file.setReadable(false);
                openFile = file;
            }
        } catch (IOException e) {
            throw new IOException("can't create file");
        }
        return new byte[]{0, 4, 0, 0}; // ACK packet - client starts writing to file
    }

    private byte[] dataRQ(byte[] massage) throws Exception {
        if (openFile == null) throw new FileNotFoundException("no open file");
        // in data packet, the first 6 are info about the data
        TftpServerUtils.writeInto(openFile, TftpServerUtils.getPartOfArray(massage, 6));
        if (massage.length < TftpServerUtils.MAX_PACKET_LENGTH + 6) {
            //openFile.setReadable(true);
            //openFile.setReadOnly();
            bCast(TftpServerUtils.concatArrays(new byte[]{0, 9, 1}, openFile.getName().getBytes(), new byte[]{0}));
            openFile = null;
        }
        // indexes 4 and 5 are the block number, therefore we send ACK of block {4,5}
        return new byte[]{0, 4, massage[4], massage[5]};
    }

    private byte[] AckRQ(byte[] massage) {
        if (openFile == null) return null;
        // in ACK packet 2 and 3 are the indexes of the block number
        short currentPart = TftpServerUtils.convertBytesToShort(massage[2], massage[3]);
        currentPart++;
        byte[] currentMessage = null;
        try {
            currentMessage = TftpServerUtils.readPartOfFile(openFile, currentPart);
        } catch (IOException ex) {
            throw new RuntimeException("can't read from file");
        }
        if (currentMessage == null) return null;
        if (currentMessage.length < TftpServerUtils.MAX_PACKET_LENGTH) openFile = null;
        return TftpServerUtils.createDataPacket(currentPart, currentMessage);
    }

    private byte[] dirRQ(byte[] massage) {
        File directory = TftpServerUtils.getFilesDirectory();
        File[] files = directory.listFiles();
        LinkedList<Byte> fileNamesList = new LinkedList<>();
        if (files != null) {
            for (File file : files) {
                if (!file.canRead()) continue;
                for (Byte letter : file.getName().getBytes())
                    fileNamesList.add(letter);
                fileNamesList.add((byte) 0);
            }
            if (!fileNamesList.isEmpty()) fileNamesList.removeLast();
        }
        return TftpServerUtils.createDataPacket((short) 1, TftpServerUtils.convertListToArray(fileNamesList));
    }

    private byte[] LogRQ(byte[] massage) throws IOException {
        // in the definition of LOGRQ packet, the first two bytes are OP and the last one is 0
        String userName =new String(TftpServerUtils.getPartOfArray(massage, 2, massage.length - 2));
        ownerId = userName.hashCode();
        if (!connections.canConnect(ownerId)) return TftpServerUtils.getError(7);
        connections.connect(ownerId, handler);
        isConnected = true;
        return new byte[]{0, 4, 0, 0};
    }

    private byte[] delRQ(byte[] massage) {
        // in the definition of DELRQ packet, the first two bytes are OP and the last one is 0
        String filename =new String(TftpServerUtils.getPartOfArray(massage, 2, massage.length - 2));
        if (!TftpServerUtils.isExists(filename)) return TftpServerUtils.getError(1);
        TftpServerUtils.getFile(filename).delete();
        bCast(TftpServerUtils.concatArrays(new byte[]{0, 9, 0}, filename.getBytes(), new byte[]{0}));
        return new byte[]{0, 4, 0, 0};
    }

    private void bCast(byte[] message) {
        connections.bCast(message, this.ownerId);
    }

    private byte[] discRQ(byte[] message) {
        connections.disconnect(ownerId);
        shouldTerminate = true;
        return new byte[]{0, 4, 0, 0};
    }
}
