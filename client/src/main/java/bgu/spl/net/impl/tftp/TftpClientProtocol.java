package bgu.spl.net.impl.tftp;

import bgu.spl.net.api.MessagingProtocol;
import bgu.spl.net.impl.Util;

import java.io.*;
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
        this.request = request.None;
        this.partedOutput = new LinkedList<Byte>();
        this.requestedFile = null;
        this.messageToSend = null;
        this.recievedAnswer = false;
        this.terminate = false;
    }

    @Override
    public byte[] process(byte[] answer) {
        byte[] result = null;
        switch (answer[1]) {
            case 3: //data packet
                if (request == Util.OP.DIRQ) {
                    System.out.println("files in dir:");
                    for (int i = 6; i < answer.length; i++) {
                        if (answer[i] == 0) {
                            System.out.println(new String(Util.convertListToArray(partedOutput)));
                            partedOutput.clear();
                        } else partedOutput.add(answer[i]);
                    }
                    if (answer.length > 6 & answer.length < Util.MAX_PACKET_LENGTH + 6) {
                        System.out.println(new String(Util.convertListToArray(partedOutput)));
                        partedOutput.clear();
                    }
                } else if (request == Util.OP.RRQ) {
                    try {
                        if (!requestedFile.exists()) {
                            requestedFile.createNewFile(); // if it exists does nothing
                            requestedFile.setReadable(false);
                        }
                        BufferedWriter writer =
                                new BufferedWriter(new FileWriter(requestedFile.getAbsoluteFile(), true));
                        byte[] onlyData = Arrays.copyOfRange(answer, 6, answer.length);
                        // Write content to the file
                        writer.write(new String(onlyData));
                        // Ensure content is flushed to the file
                        writer.flush();
                        if (onlyData.length < 512) {
                            requestedFile.setReadable(true);
                            requestedFile.setReadOnly();
                            System.out.println(requestedFile.getName() + " added successfully");
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
                if (messageToSend == null) {
                    recievedAnswer = true;
                    if (request == Util.OP.DISC) terminate = true;
                    else if (request == Util.OP.LOGRQ) System.out.println("logged in successfully");
                    else if (request == Util.OP.WRQ) {
                        if (messageToSend == null) {
                            System.out.println("finished uploading file");
                            break;
                        }

                    }

                    request = Util.OP.None;
                    break;
                }
                short currentPart = Util.convertBytesToShort(answer[2], answer[3]);
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
            case 9: //bcast
                String status = (answer[2] == 1) ? "add" : "del";
                System.out.println("BCAST" + status + new String(Util.cutFromEnd(answer, 1)));
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
        if (read) {
            requestedFile = Util.getFile(fileName);
            if (requestedFile.exists()) requestedFile.delete();
        } else try {
            File file = Util.getFile(fileName);
            if (!Util.fileExists(fileName)) throw new RuntimeException("doesn't have file");
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
