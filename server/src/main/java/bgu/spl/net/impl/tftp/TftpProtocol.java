package bgu.spl.net.impl.tftp;

import bgu.spl.net.api.BidiMessagingProtocol;
import bgu.spl.net.srv.BlockingConnectionHandler;
import bgu.spl.net.srv.Connections;

import java.io.*;
import java.nio.file.Files;
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
        this.shouldTerminate=false;
        this.ownerId = connectionId;
        this.isConnected = false;
        this.handler = handler;
        this.openFile = null;
    }

    @Override
    public byte[] process(byte[] message) throws Exception {
        byte type = message[1];
        byte[] data = new byte[message.length - 2];
        System.arraycopy(message, 2, data, 0, data.length);
        if (data.length > 0 && data[data.length - 1] == 0) data = Util.cutFromEnd(data, 1);
        //if (!isConnected & type != 7) return Util.getError(new byte[]{0, 6});
        if (isConnected & type == 7) return Util.getError(new byte[]{0, 7});
        switch (type) {
            case 1:
                return RRQ(data);
            case 2:
                return WRQ(data);
            case 3:
                return dataRQ(data);
            case 4:
                return AckRQ(data);
            case 6:
                return dirRQ(data);
            case 7:
                return LogRQ(data);
            case 8:
                return delRQ(data);
            case 0xa:
                return discRQ(data);

            //case 5: not receiving errors from client
            //case 9: not receiving bCast from client

            default:
                throw new UnsupportedOperationException("not to be used from client");
        }
    }

    @Override
    public boolean shouldTerminate() {
        return shouldTerminate;
    }

    private byte[] RRQ(byte[] filename) throws Exception {
        File file = Util.getFile(new String(filename));
        if (!file.exists()) return Util.getError(new byte[]{0, 1});
        openFile = file;
        return Util.createDataPacket((short) 1, Util.readPartOfFile(openFile, (short) 1));
    }

    private byte[] WRQ(byte[] fileName) throws IOException {
        File file = Util.getFile(new String(fileName));
        try {
            if (!file.createNewFile())
                return Util.getError(new byte[]{0, 5}); //ERROR - FILE ALREADY EXISTS
            else {
                //file.setReadable(false);
                openFile = file;
            }
        } catch (IOException e) {
            throw new IOException("can't create file");
        }
        return new byte[]{0, 4, 0, 0}; // ACK packet - client starts writing to file
    }

    private byte[] dataRQ(byte[] data) throws Exception {
        if (openFile == null) throw new FileNotFoundException("no open file");
        byte[] onlyData = new byte[data.length - 4];
        System.arraycopy(data, 4, onlyData, 0, onlyData.length);
        Util.writeInto(openFile, onlyData);
        if (onlyData.length < Util.MAX_PACKET_LENGTH) {
            //openFile.setReadable(true);
            //openFile.setReadOnly();
            bCast(Util.addZero(Util.concurArrays(new byte[]{0, 9, 1}, openFile.getName().getBytes())));
            openFile = null;
        }
        return new byte[]{0, 4, data[2], data[3]};
    }

    private byte[] AckRQ(byte[] lastACK) {
        if (openFile == null) return null;
        short currentPart = Util.convertBytesToShort(lastACK[0], lastACK[1]);
        currentPart++;
        byte[] currentMessage=null;
        try {
            currentMessage = Util.readPartOfFile(openFile, currentPart);
        } catch (Exception ignored){}
        if (currentMessage==null) return null;
        if (currentMessage.length<Util.MAX_PACKET_LENGTH) openFile = null;
        return Util.createDataPacket(currentPart, currentMessage);
    }

    private byte[] dirRQ(byte[] data) {
        File directory = Util.getFilesDirectory();
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
        return Util.createDataPacket((short) 1, Util.convertListToArray(fileNamesList));
    }

    private byte[] LogRQ(byte[] message) throws IOException {
        String userName = new String(message);
        ownerId = userName.hashCode();
        if (!connections.canConnect(ownerId)) return Util.getError(new byte[]{0, 7});
        connections.connect(ownerId, handler);
        isConnected = true;
        return new byte[]{0, 4, 0, 0};
    }

    private byte[] delRQ(byte[] data) {
        String filename = new String(data);
        if (!Util.isExists(filename)) return Util.getError(new byte[]{0, 1});
        Util.getFile(filename).delete();
        bCast(Util.addZero(
                Util.concurArrays(new byte[]{0, 9, 0}, data)));
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
