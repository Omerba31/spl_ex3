package bgu.spl.net.impl.tftp;

import bgu.spl.net.api.MessageEncoderDecoder;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import bgu.spl.net.Util;

public class TftpEncoderDecoder implements MessageEncoderDecoder<byte[]> {
    private List<Byte> bytes = new LinkedList<>();

    @Override
    public byte[] decodeNextByte(byte nextByte) {
        bytes.add(nextByte);

        if (bytes.size() <= 1) return null;
        byte[] retBytes = null;

        int op = bytes.get(1);
        if (op > 0xa | op < 1)
            throw new IllegalArgumentException("OP not valid");
        switch (op) {
            default:
                if (bytes.size() - 6 == 512)
                    retBytes = Util.convertListToArray(bytes);
                break;
            case 3:
                // data packet
                if (bytes.get(bytes.size() - 1) == 0 | bytes.size() - 6 == 512)
                    retBytes = Util.convertListToArray(bytes);
                break;
            case 4:
                if (bytes.size() == 4) retBytes = Util.convertListToArray(bytes);
                break;
            case 6:
            case 0xa:
                retBytes = Util.convertListToArray(bytes);
                break;
        }
        if (retBytes != null) bytes.clear();
        return retBytes;
    }

    @Override
    public byte[] encode(byte[] message) throws IllegalArgumentException {
        if (message[0] == 0) throw new IllegalArgumentException();
        int op = message[1];
        if (op == 3 & message.length - 6 > 512) //data packet
            throw new IllegalArgumentException("too long Packet");
        if ((op == 1 | op == 2 | op == 5 | op == 7 | op == 8 | op == 9)
                & message[message.length - 1] != 0)
            throw new IllegalArgumentException("missing 0");

        return message;
    }

}