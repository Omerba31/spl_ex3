package bgu.spl.net.impl.tftp;

import bgu.spl.net.UtilClient;
import bgu.spl.net.api.MessagingProtocol;

import java.io.*;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

public class TftpClientProtocol implements MessagingProtocol<byte[]> {
    private UtilClient.OP request;
    private List<Byte> partedOutput;
    private File requestedFile;
    public Boolean recievedAnswer;
    private byte[] messageToSend;
    private boolean terminate;

    public TftpClientProtocol() {
        this.request = request.None;
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
                if (request == UtilClient.OP.DIRQ) {
                    System.out.println("files in dir:");
                    int counter = 0;
                    for (int i = 6; i < answer.length; i++) {
                        if (answer[i] == 0) {
                            counter++;
                            System.out.println("file " + counter + ": " + new String(UtilClient.convertListToArray(partedOutput)));
                            partedOutput.clear();
                        } else partedOutput.add(answer[i]);
                    }
                    if (answer.length > 6 & answer.length < UtilClient.MAX_PACKET_LENGTH + 6) {
                        System.out.println("file " + counter + ": " + new String(UtilClient.convertListToArray(partedOutput)));
                        partedOutput.clear();
                    }
                } else if (request == UtilClient.OP.RRQ) {
                    try {
                        boolean append = true;
                        if (!requestedFile.exists()) {
                            requestedFile.createNewFile(); // if it exists does nothing
                            requestedFile.setReadable(false);
                        } else if (UtilClient.convertBytesToShort(answer[2], answer[3]) == 0) append = false;
                        BufferedWriter writer =
                                new BufferedWriter(new FileWriter(requestedFile.getAbsoluteFile(), append));
                        byte[] onlyData = Arrays.copyOfRange(answer, 6, answer.length);
                        // Write content to the file
                        writer.write(new String(onlyData));
                        // Ensure content is flushed to the file
                        writer.flush();
                        if (onlyData.length < 512) {
                            requestedFile.setReadable(true);
                            //requestedFile.setReadOnly();
                            System.out.println(requestedFile.getName() + " added successfully");
                            requestedFile = null;
                        }
                    } catch (IOException ignored) {
                    }
                }
                if (answer.length < UtilClient.MAX_PACKET_LENGTH + 6) {
                    request = UtilClient.OP.None;
                    recievedAnswer = true;
                } else result = new byte[]{0, 4, answer[4], answer[5]}; // ACK packet
                break;
            case 4: //ACK packet
                if (messageToSend == null) {
                    recievedAnswer = true;
                    if (request == UtilClient.OP.DISC) terminate = true;
                    else if (request == UtilClient.OP.LOGRQ) System.out.println("logged in successfully");
                    else if (request == UtilClient.OP.WRQ) {
                        System.out.println("finished uploading file");
                        break;
                    }

                    request = UtilClient.OP.None;
                    break;
                }
                short currentPart = UtilClient.convertBytesToShort(answer[2], answer[3]);
                currentPart++;
                byte[] currentMessage = messageToSend;
                if (UtilClient.isLastPart(messageToSend, currentPart)) messageToSend = null;
                result = UtilClient.createDataPacket(currentPart, currentMessage);
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

    public void inform(UtilClient.OP request) {
        this.request = request;
    }

    public void inform(String fileName, boolean read) {
        if (read) {
            if (UtilClient.fileExists(fileName)) UtilClient.getFile(fileName).delete();
            requestedFile = UtilClient.getFile(fileName);
        } else try {
            if (!UtilClient.fileExists(fileName)) throw new RuntimeException("doesn't have file"); ////
            File file = UtilClient.getFile(fileName);
            BufferedReader reader = new BufferedReader(new FileReader(file.getAbsoluteFile()));
            StringBuilder content = new StringBuilder();
            String line;
            // Read each line from the file and append it to the content string
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }
            messageToSend = content.toString().getBytes();
        } catch (Exception ignored) {
        }
    }

}
