package bgu.spl.net.impl.tftp;

import bgu.spl.net.Util;
import bgu.spl.net.api.MessagingProtocol;

import java.io.*;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

public class TftpClientProtocol implements MessagingProtocol<byte[]> {
    private Util.OP request;
    private List<Byte> partedOutput;
    private File requestedFile;
    public Boolean recievedAnswer;
    private byte[] messageToSend;
    private boolean terminate;

    public TftpClientProtocol() {
        this.request = Util.OP.None;
        this.partedOutput = new LinkedList<>();
        this.requestedFile = null;
        this.messageToSend = null;
        this.recievedAnswer = false;
        this.terminate = false;
    }

    @Override
    public byte[] process(byte[] answer) {
        byte[] result = null;
        //System.out.println(answer);
        switch (answer[1]) {
            case 3: //data packet
                if (request == Util.OP.DIRQ) {
                    System.out.println("files in dir:");
                    int counter = 0;
                    for (int i = 6; i < answer.length; i++) {
                        if (answer[i] == 0) {
                            counter++;
                            System.out.println("file " + counter + ": " + new String(Util.convertListToArray(partedOutput)));
                            partedOutput.clear();
                        } else partedOutput.add(answer[i]);
                    }
                    if (answer.length > 6 & answer.length < Util.MAX_PACKET_LENGTH + 6) {
                        System.out.println("file " + counter + ": " + new String(Util.convertListToArray(partedOutput)));
                        partedOutput.clear();
                    }
                } else if (request == Util.OP.RRQ) {
                    try {
                        //boolean append = true;
                        if (!requestedFile.exists()) {
                            requestedFile.createNewFile(); // if it exists does nothing
                            //requestedFile.setReadable(false);
                        } //else if (Util.convertBytesToShort(answer[2], answer[3]) == 0) append = false;
                        byte[] onlyData = Arrays.copyOfRange(answer, 6, answer.length);
                        Util.writeInto(requestedFile,onlyData);
                        if (onlyData.length < 512) {
                            //requestedFile.setReadable(true);
                            //requestedFile.setReadOnly();
                            System.out.println("RRQ " + requestedFile.getName() + " complete");
                            requestedFile = null;
                        }
                    } catch (IOException ignored) {
                    }
                }
                if (answer.length < Util.MAX_PACKET_LENGTH + 6) {
                    request = Util.OP.None;
                    recievedAnswer = true;
                } else result = new byte[]{0, 4, answer[4], answer[5]}; // ACK packet
                break;
            case 4: //ACK packet
                short currentPart = Util.convertBytesToShort(answer[2], answer[3]);
                System.out.println("ACK " + currentPart);
                if (messageToSend == null) {
                    recievedAnswer = true;
                    if (request == Util.OP.DISC) terminate = true;
                    else if (request == Util.OP.WRQ) {
                        System.out.println("WRQ "+ requestedFile.getName() + " completed");
                        requestedFile = null;
                        break;
                    }

                    request = Util.OP.None;
                    break;
                }
                currentPart++;
                byte[] currentMessage = messageToSend;
                if (Util.isLastPart(messageToSend, currentPart)) messageToSend = null;
                result = Util.createDataPacket(currentPart, currentMessage);
                break;
            case 5: //error packet
                System.out.println("ERROR " + answer[3] + ": " + new String(
                        Arrays.copyOfRange(answer, 4, answer.length - 1)));
                recievedAnswer = true;
                break;
            case 9: //bCast
                String status = (answer[2] == 1) ? "add - " : "del - ";
                System.out.println("bCast: " + status +
                        new String(Arrays.copyOfRange(answer, 3, answer.length - 1)));
                break;
        }
        return result;
    }

    @Override
    public boolean shouldTerminate() {
        return terminate;
    }

    public void inform(Util.OP request) {
        this.request = request;
    }

    public void inform(String fileName, boolean read) {
        if (read && Util.isExists(fileName)) Util.getFile(fileName).delete();
        if(!read) try {
            if (!Util.isExists(fileName)) throw new RuntimeException("doesn't have file");
            File file = Util.getFile(fileName);
            messageToSend = Files.readAllBytes(file.getAbsoluteFile().toPath());
            /*BufferedReader reader = new BufferedReader(new FileReader(file.getAbsoluteFile()));
            StringBuilder content = new StringBuilder();
            String line;
            // Read each line from the file and append it to the content string
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }
            messageToSend = content.toString().getBytes();*/
        } catch (Exception ignored) {
        }
        requestedFile = Util.getFile(fileName);
    }

}
