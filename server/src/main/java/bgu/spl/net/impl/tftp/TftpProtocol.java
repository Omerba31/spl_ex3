package bgu.spl.net.impl.tftp;

import bgu.spl.net.UtilServer;
import bgu.spl.net.api.BidiMessagingProtocol;
import bgu.spl.net.srv.BlockingConnectionHandler;
import bgu.spl.net.srv.Connections;

import java.io.*;
import java.util.Arrays;
import java.util.LinkedList;

public class TftpProtocol implements BidiMessagingProtocol<byte[]> {

    private boolean shouldTerminate = false;
    private volatile TftpConnections<byte[]> connections;
    private int ownerId;
    private boolean isConnected;
    private byte[] message = null;
    private File openFile = null;
    private BlockingConnectionHandler handler;


    /*public TftpProtocol(int connectionId, Connections<byte[]> connections) {
        start(connectionId, connections);
    }*/

    @Override
    public void start(int connectionId, Connections<byte[]> connections, BlockingConnectionHandler<byte[]> handler) {
        this.connections = (TftpConnections<byte[]>) connections;
        this.ownerId = connectionId;
        this.isConnected = false;
        this.handler = handler;
    }

    @Override
    public byte[] process(byte[] message) throws Exception {
        byte type = message[1];
        byte[] data = new byte[message.length - 2];
        System.arraycopy(message, 2, data, 0, data.length);
        if (data.length > 0 && data[data.length - 1] == 0) data = UtilServer.cutFromEnd(data, 1);
        if (!isConnected & type != 7) return UtilServer.getError(new byte[]{0, 6});
        if (isConnected & type == 7) return UtilServer.getError(new byte[]{0, 7});
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
        File file = UtilServer.getFile(new String(filename));
        if (!file.exists()) return UtilServer.getError(new byte[]{0, 1});
        BufferedReader reader = new BufferedReader(new FileReader(file.getAbsoluteFile()));
        StringBuilder content = new StringBuilder();
        String line;
        // Read each line from the file and append it to the content string
        while ((line = reader.readLine()) != null) {
            content.append(line).append("\n");
        }
        message = content.toString().getBytes();
        System.out.println(new String(message));
        byte[] currentMessage = message;
        if (UtilServer.isLastPart(message, 1)) message = null;
        return UtilServer.createDataPacket((short) 1, currentMessage);
    }

    private byte[] WRQ(byte[] fileName) throws IOException {
        String stringFileName = new String(fileName);
        File file = UtilServer.getFile(stringFileName);
        try {
            if (!file.createNewFile())
                return UtilServer.getError(new byte[]{0, 2}); //ERROR - FILE ALREADY EXISTS
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
                     new BufferedWriter(new FileWriter(openFile.getAbsoluteFile(), true))) {
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
            bCast(UtilServer.addZero(
                    UtilServer.concurArrays(new byte[]{0, 9, 1}, openFile.getName().getBytes())));
            openFile = null;
        }
        return new byte[]{0, 4, data[2], data[3]};
    }

    private byte[] AckRQ(byte[] lastACK) {
        if (message == null) return null;
        short currentPart = UtilServer.convertBytesToShort(lastACK[0], lastACK[1]);
        currentPart++;
        byte[] currentMessage = message;
        if (UtilServer.isLastPart(message, currentPart)) message = null;
        byte [] ACK = UtilServer.createDataPacket(currentPart, currentMessage);
        System.out.println(new String(ACK));
        return ACK;
    }

    private byte[] dirRQ(byte[] data) {
        File directory = UtilServer.getFilesDirectory();
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
        message = UtilServer.convertListToArray(fileNamesList);
        byte[] retByte = message;
        if (UtilServer.isLastPart(message, 1)) message = null;
        return UtilServer.createDataPacket((short) 1, retByte);
    }

    private byte[] LogRQ(byte[] message) throws IOException {
        String userName = new String(message);
        ownerId = userName.hashCode();
        byte[] cpMessage;
        byte[] opCode;
        if (connections.canConnect(ownerId)) {
            connections.connect(ownerId, handler);
            opCode = new byte[]{0, 4};
            cpMessage = new byte[]{0, 0};
            isConnected = true;
        } else {
            return UtilServer.getError(new byte[]{0, 7});

        }
        return UtilServer.addZero(UtilServer.concurArrays(opCode, cpMessage));
    }

    private byte[] delRQ(byte[] data) {
        String filename = new String(data);
        if (!UtilServer.fileExists(filename)) return UtilServer.getError(new byte[]{0, 1});
        UtilServer.getFile(filename).delete();
        bCast(UtilServer.addZero(
                UtilServer.concurArrays(new byte[]{0, 9, 0}, data)));
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
