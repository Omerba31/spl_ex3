package bgu.spl.net.impl.tftp;

import bgu.spl.net.Util;
import bgu.spl.net.api.BidiMessagingProtocol;
import bgu.spl.net.srv.BlockingConnectionHandler;
import bgu.spl.net.srv.Connections;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.Files;

import java.io.*;
import java.net.Socket;
import java.util.Arrays;
import java.util.LinkedList;

public class TftpProtocol implements BidiMessagingProtocol<byte[]> {

    private boolean shouldTerminate = false;
    private volatile TftpConnections<byte[]> connections;
    private int ownerId;
    private boolean isConnected;
    private byte[] message = null;
    private File openFile = null;

    /*public TftpProtocol(int connectionId, Connections<byte[]> connections) {
        start(connectionId, connections);
    }*/

    @Override
    public void start(int connectionId, Connections<byte[]> connections) {
        this.connections = (TftpConnections<byte[]>) connections;
        this.ownerId = connectionId;
        this.isConnected = false;
    }

    @Override
    public byte[] process(byte[] message) throws Exception {
        byte type = message[1];
        byte[] data = new byte[message.length - 2];
        System.arraycopy(message, 2, data, 0, data.length);
        if (data.length > 0 && data[data.length - 1] == 0) data = Util.cutFromEnd(data, 1);
        //if (!isConnected & type != 7) return Util.getError(new byte[]{0, 6});
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
        /*File directory = new File("Files");
        File file = new File(directory, new String(filename));*/
        if (!file.exists()) return Util.getError(new byte[]{0, 1});
        BufferedReader reader = new BufferedReader(new FileReader(file.getAbsoluteFile()));
        StringBuilder content = new StringBuilder();
        String line;
        // Read each line from the file and append it to the content string
        while ((line = reader.readLine()) != null) {
            content.append(line).append("\n");
        }
        message = content.toString().getBytes();
        byte[] currentMessage = message;
        if (Util.isLastPart(message, 1)) message = null;
        return Util.createDataPacket((short) 1, currentMessage);
    }

    private byte[] WRQ(byte[] fileName) throws IOException {
        String stringFileName = new String(fileName);
        File file = Util.getFile(stringFileName);
        try {
            if (!file.createNewFile())
                return Util.getError(new byte[]{0, 2}); //ERROR - FILE ALREADY EXISTS
            else {
                file.setReadable(false);
                openFile = file;
            }
        } catch (IOException e) {
            throw new IOException("can't create file");
        }
        return new byte[]{0, 4, 0, 0}; // ACK packet - client starts writing to file
    }

    private byte[] dataRQ(byte[] data) throws Exception {
        if (openFile == null) throw new FileNotFoundException();
        byte[] onlyData = new byte[data.length - 4];
        System.arraycopy(data, 4, onlyData, 0, onlyData.length);

        try (BufferedWriter writer =
                     new BufferedWriter(new FileWriter(openFile.getAbsoluteFile(),true))) {
            // Write content to the file
            writer.write(new String(onlyData));
            // Ensure content is flushed to the file
            writer.flush();
        } catch (IOException e) {
            throw new IOException(e);
        }
        if (onlyData.length < 512) {
            openFile.setReadable(true);
            openFile.setReadOnly();
            byte[] bCastPacket = Util.concurArrays(new byte[]{0, 9, 1}, openFile.getName().getBytes());
            //bCast(bCastPacket);
            openFile = null;
        }
        return new byte[]{0,4,data[2], data[3]};
    }

    private byte[] AckRQ(byte[] lastACK) {
        if (message == null) return null;
        short currentPart = Util.convertBytesToShort(lastACK[0], lastACK[1]);
        currentPart++;
        byte[] currentMessage = message;
        if (Util.isLastPart(message, currentPart)) message = null;
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
        message = Util.convertListToArray(fileNamesList);
        byte[] retByte = message;
        if (Util.isLastPart(message, 1)) message = null;
        return Util.createDataPacket((short) 1, retByte);
    }

    private byte[] LogRQ(byte[] message) {
        String userName = new String(message);
        int connectionId = userName.hashCode();
        byte[] cpMessage;
        byte[] opCode;
        if (connections.canConnect(ownerId)) {
            BlockingConnectionHandler<byte[]> blockingConnectionHandler = new BlockingConnectionHandler<>(
                    new Socket(), new TftpEncoderDecoder(), this);
            //was: connections.connect(connectionId, blockingConnectionHandler);
            connections.connect(ownerId, blockingConnectionHandler);
            opCode = new byte[]{0, 4};
            cpMessage = new byte[]{0, 0};
            isConnected = true;
        } else {
            opCode = new byte[]{0, 5, 0, 7};
            cpMessage = "User already logged in – Login username already connected.".getBytes();
        }
        return Util.concurArrays(opCode, cpMessage);
    }

    private byte[] delRQ(byte[] data) {
        String filename = new String(data);
        File file = Util.getFile(filename);

        if (Util.fileExists(filename)){
            Util.getFile(filename).delete();
            return new byte[]{0,4,0,0};
        }
        return Util.getError(new byte[]{0, 1});
        //bCast(Util.concurArrays(new byte[]{0, 9, 0}, data));
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
