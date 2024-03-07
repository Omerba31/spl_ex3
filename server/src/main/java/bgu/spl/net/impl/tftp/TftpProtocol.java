package bgu.spl.net.impl.tftp;

import bgu.spl.net.Util;
import bgu.spl.net.api.BidiMessagingProtocol;
import bgu.spl.net.srv.BaseConnections;
import bgu.spl.net.srv.BlockingConnectionHandler;
import bgu.spl.net.srv.Connections;

import java.io.*;
import java.net.Socket;
import java.nio.file.NoSuchFileException;
import java.util.Arrays;

public class TftpProtocol implements BidiMessagingProtocol<byte[]> {

    private int MAX_PACKET_LENGTH = 512;
    private boolean shouldTerminate = false;
    private BaseConnections<byte[]> connections;
    private int ownerId;
    private boolean isConnected;
    private byte[] message = null;
    private File openFile = null;

    public TftpProtocol(int connectionId, Connections<byte[]> connections) {
        start(connectionId, connections);
    }

    @Override
    public void start(int connectionId, Connections<byte[]> connections) {
        this.connections = (BaseConnections<byte[]>) connections;
        this.ownerId = connectionId;
        this.isConnected = false;
    }

    @Override
    public byte[] process(byte[] message) throws Exception {
        byte type = message[1];
        byte[] data = new byte[message.length - 2];
        System.arraycopy(message, 2, data, 0, data.length);
        if (data[data.length - 1] == 0) data = Util.cutFromEnd(data, 1);
        if (!isConnected & type != 7) return Util.getError(new byte[]{0, 6});

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
            case 7:
                return LogRQ(data);
            case 8:
                return delRQ(data);
            case 0xa:

                //case 5: not receiving errors from client
                //case 9: not receiving bCast from client

            default:
                throw new UnsupportedOperationException("not to be used from client");
        }
    }


    @Override
    public boolean shouldTerminate() {
        shouldTerminate = true;
        // TODO implement this
        throw new UnsupportedOperationException("Unimplemented method 'shouldTerminate'");
    }


    private byte[] RRQ(byte[] filename) throws Exception {
        File file = Util.getFile(Arrays.toString(filename));
        if (!file.exists()) return Util.getError(new byte[]{0, 1});
        BufferedReader reader = new BufferedReader(new FileReader(file.getAbsoluteFile()));
        StringBuilder content = new StringBuilder();
        String line;
        // Read each line from the file and append it to the content string
        while ((line = reader.readLine()) != null) {
            content.append(line).append("\n");
        }
        message = content.toString().getBytes();
        return Util.getPartArray(message, 0);
    }

    private byte[] WRQ(byte[] fileName) throws IOException {
        String stringFileName = Arrays.toString(fileName);
        File file = Util.getFile(stringFileName);
        try {
            if (!file.createNewFile())
                return Util.getError(new byte[]{0, 2}); //ERROR - FILE EXISTS
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
        if (openFile == null)
            throw new FileNotFoundException();
        byte[] onlyData = new byte[data.length - 4];
        System.arraycopy(data, 4, onlyData, 0, onlyData.length);

        try (BufferedWriter writer =
                     new BufferedWriter(new FileWriter(openFile.getAbsoluteFile()))) {
            // Write content to the file
            writer.write(Arrays.toString(onlyData));
            // Ensure content is flushed to the file
            writer.flush();
        } catch (IOException e) {
            throw new IOException(e);
        }
        if (onlyData.length < 512) {
            openFile.setReadable(true);
            openFile.setReadOnly();
            byte[] bCastPacket = Util.concurArrays(new byte[]{0, 9, 1}, openFile.getName().getBytes());
            bCast(bCastPacket);
            openFile = null;
        }
        return AckRQ(new byte[]{data[4], data[5]});
    }

    private byte[] AckRQ(byte[] lastACK) {
        if (message == null) return null;
        int currentPart = Util.byteHexArrayToInteger(lastACK) + 1;
        byte[] currentMessage = Util.getPartArray(message, currentPart);
        if (Util.isLastPart(message, currentPart)) message = null;
        byte[] opByte = new byte[]{0, 3};
        return Util.concurArrays(opByte, currentMessage);
    }

    private byte[] LogRQ(byte[] message) {
        String userName = new String(message);
        int connectionId = userName.hashCode();
        byte[] cpMessage = null;
        byte[] opCode;
        if (connections.canConnect(connectionId)) {
            BlockingConnectionHandler<byte[]> blockingConnectionHandler = new BlockingConnectionHandler<>(
                    new Socket(), new TftpEncoderDecoder(), new TftpProtocol(connectionId, connections));
            connections.connect(connectionId, blockingConnectionHandler);
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
        String filename = Arrays.toString(data);
        if (!Util.getFile(filename).delete())
            return Util.getError(new byte[]{0, 1});
        bCast(Util.concurArrays(new byte[]{0, 9, 0}, filename.getBytes()));
        return null;
    }

    private void bCast(byte[] message) {
        connections.bCast(message, this.ownerId);
    }
}
