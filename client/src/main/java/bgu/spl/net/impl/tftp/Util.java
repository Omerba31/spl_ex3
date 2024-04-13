package bgu.spl.net.impl.tftp;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.NoSuchElementException;

public class Util {
    public static final short MAX_PACKET_LENGTH = (short)512;
    public static boolean runningOnLinux=true;

    public static boolean isLastPart(byte[] firstMessage, int currentPart) {
        return firstMessage.length < currentPart * MAX_PACKET_LENGTH;
    }

    public static byte[] convertListToArray(List<Byte> list) {
        byte[] retByte = new byte[list.size()];
        for (int i = 0; i < list.size(); i++) {
            retByte[i] = list.get(i);
        }
        return retByte;
    }

    public static byte[] hexToBytes(byte[] hexBytes) {
        byte[] bytes = new byte[hexBytes.length / 2];
        for (int i = 0; i < hexBytes.length; i += 2) {
            String hexPair = new String(hexBytes, i, 2, StandardCharsets.US_ASCII);
            bytes[i / 2] = (byte) Integer.parseInt(hexPair, 16);
        }
        return bytes;
    }

    public static byte[] getPartArray(byte[] src, short blockNumber) {
        int numOfBlocksToRemove = blockNumber - 1;
        int copyLength = src.length - (MAX_PACKET_LENGTH * numOfBlocksToRemove);
        if (copyLength < 0)
            throw new NoSuchElementException("block number: " + blockNumber + " doesn't exist");
        if (copyLength == 0) // for check
            System.out.println("empty packet");
        if (copyLength > MAX_PACKET_LENGTH) // NOT legal sized packet
            copyLength = MAX_PACKET_LENGTH;
        byte[] retByte = new byte[copyLength];
        System.arraycopy(src, numOfBlocksToRemove * MAX_PACKET_LENGTH, retByte, 0, copyLength);
        return retByte;
    }

    /*public static short byteHexArrayToShort(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X", b)); // Convert byte to hexadecimal string
        }
        return Short.parseShort(sb.toString());
    }*/

    public static byte[] convertShortToByteArray(short s) {
        return new byte[]{(byte) ((s >> 8) & 0xFF), (byte) (s & 0xff)};
    }

    public static short convertBytesToShort(byte byte1, byte byte2) {
        return (short) (((byte1 & 0xFF) << 8) | (byte2 & 0xFF));
    }

    public static byte[] createDataPacket(short blockNumber, byte[] message) {
        //byte[] data = getPartArray(message, blockNumber);
        byte[] info = concurArrays(convertShortToByteArray((short) message.length),
                convertShortToByteArray(blockNumber));
        info = concurArrays(new byte[]{0, 3}, info);
        return Util.concurArrays(info, message);
    }

    public static byte[] addZero(byte[] message) {
        return concurArrays(message, new byte[]{0});
    }

    public static byte[] concurArrays(byte[] a1, byte[] a2) {
        byte[] retByte = new byte[a1.length + a2.length];
        System.arraycopy(a1, 0, retByte, 0, a1.length);
        System.arraycopy(a2, 0, retByte, a1.length, a2.length);
        return retByte;
    }

    public static byte[] cutFromEnd(byte[] arr, int cut) {
        byte[] retByte = new byte[arr.length - cut];
        System.arraycopy(arr, 0, retByte, 0, retByte.length);
        return retByte;
    }

    public static File getFilesDirectory() {
        String path;
        if(!runningOnLinux) path = System.getProperty("user.dir") + "\\client\\Files";
        else path = "Files" + File.separator;
        return new File(path);
    }

    public static File getFile(String fileName) {
        File[] arr = getFilesDirectory().listFiles((dir, name) -> name.equals(fileName));
        if (arr == null || arr.length == 0) return new File(getFilesDirectory(), fileName);
        return arr[0];
        //return new File(getFilesDirectory(), fileName);
    }

    public static boolean isExists(String filename) {
        File directory = getFilesDirectory();
        File[] arr = directory.listFiles((dir, name) -> name.equals(filename));
        return (arr != null && arr.length > 0);
    }
    public static void writeInto(File destination, byte[] data) throws IOException {
        FileOutputStream out = new FileOutputStream(destination,true);
        out.write(data);
        out.close();
    }
    public static byte[] readPartOfFile(File file, short part) throws IOException {
        return readPartOfFile(file, (long) (part-1) *Util.MAX_PACKET_LENGTH, Util.MAX_PACKET_LENGTH);
    }
    private static byte[] readPartOfFile(File file, long startPosition, short bytesToRead) throws IOException {

        long fileSize = Files.size(file.toPath());
        if (bytesToRead > fileSize - startPosition) bytesToRead = (short) (fileSize - startPosition);
        //prevent error of reading outside the file

        try (FileInputStream fis = new FileInputStream(file)) {
            fis.skip(startPosition); // Move to the start position

            byte[] buffer = new byte[bytesToRead];
            int bytesRead = fis.read(buffer); // Read bytes into the buffer

            if (bytesRead != -1) {
                return buffer;
            }
        }
        return new byte[0];
    }
        public static byte[] getError(byte[] errorType) {
        if (errorType[0] != 0) throw new IllegalArgumentException("Illegal error type inserted!");
        byte[] error = Util.concurArrays(new byte[]{0, 5}, errorType);
        String errorMessage;

        switch (errorType[1]) {
            case 0:
                errorMessage = "Not defined, see error message (if any).";
                break;
            case 1:
                errorMessage = "1 File not found – RRQ DELRQ of non-existing file.";
                break;
            case 2:
                errorMessage = "Access violation – File cannot be written, read or deleted.";
                break;
            case 3:
                errorMessage = "Disk full or allocation exceeded – No room in disk.";
                break;
            case 4:
                errorMessage = "Illegal TFTP operation – Unknown Opcode.";
                break;
            case 5:
                errorMessage = "File already exists – File name exists on WRQ.";
                break;
            case 6:
                errorMessage = "User not logged in – Any opcode received before Login completes.";
                break;
            case 7:
                errorMessage = "User already logged in – Login username already connected.";
                break;
            default:
                throw new IllegalArgumentException("Illegal error type inserted!");
        }
        return Util.addZero(Util.concurArrays(error, errorMessage.getBytes()));
    }

    public static void printHexBytes(byte[] arr) {
        for (byte b : arr) {
            System.out.print(String.format("%02X ", b));
        }
    }

    public static enum OP {None, RRQ, WRQ, DATA, ACK, ERROR, DIRQ, LOGRQ, DELRQ, BCAST, DISC}

    public static OP getOpByByte(byte n) {
        if (n == 0) return OP.None;
        else if (n == 1) return OP.RRQ;
        else if (n == 2) return OP.WRQ;
        else if (n == 3) return OP.DATA;
        else if (n == 4) return OP.ACK;
        else if (n == 5) return OP.ERROR;
        else if (n == 6) return OP.DIRQ;
        else if (n == 7) return OP.LOGRQ;
        else if (n == 8) return OP.DELRQ;
        else if (n == 9) return OP.BCAST;
        else if (n == 0xa) return OP.DISC;
        throw new RuntimeException("not an OP code");
    }
}
