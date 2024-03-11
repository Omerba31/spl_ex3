package bgu.spl.net.impl;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class Util {
    public static int MAX_PACKET_LENGTH = 512;

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

    public static byte[] getPartArray(byte[] src, int blockNumber) {
        blockNumber--;
        int copyLength = MAX_PACKET_LENGTH;
        // for last blockNumber \ src.length < maxLength - we copy only the appropriate size.
        if (!(src.length - MAX_PACKET_LENGTH * blockNumber > MAX_PACKET_LENGTH))
            copyLength = src.length - MAX_PACKET_LENGTH * blockNumber;
        byte[] retByte = new byte[copyLength];
        System.arraycopy(src, blockNumber * MAX_PACKET_LENGTH, retByte, 0, copyLength);
        return retByte;
    }

    public static int byteHexArrayToInteger(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X", b)); // Convert byte to hexadecimal string
        }
        return Integer.parseInt(sb.toString());
    }

    public static byte[] createDataPacket(int blockNumber, byte[] message) {
        // calculate data size to hexadecimal
        byte byte1 = (byte) ((message.length >> 8) & 0xFF);
        byte byte2 = (byte) (message.length & 0xFF);
        byte part1 = (byte) ((blockNumber >> 8) & 0xFF);
        byte part2 = (byte) (blockNumber & 0xFF);
        byte[] data = getPartArray(message, blockNumber);
        if (data.length<MAX_PACKET_LENGTH) data = addZero(data);
        return Util.concurArrays(new byte[]{0, 3, byte1, byte2, part1, part2}, data);
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

    public static boolean isFileExist(String filename) {
        File directory = new File("Files");
        File file = new File(directory, filename);
        return file.exists();
    }

    public static File getFile(String fileName) {
        File directory = new File("Files");
        return new File(directory, fileName); //to check if file exists before - if bugs
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
        return Util.concurArrays(error, errorMessage.getBytes());
    }
    public static void printHexBytes(byte[] arr){
        for (byte b : arr) {
            System.out.print(String.format("%02X ", b));
        }
    }
}
