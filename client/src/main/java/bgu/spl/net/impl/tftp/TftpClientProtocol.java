package bgu.spl.net.impl.tftp;

import bgu.spl.net.api.MessagingProtocol;
import bgu.spl.net.impl.Util;
import bgu.spl.net.impl.srv.BaseClient;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

public class TftpClientProtocol implements MessagingProtocol<byte[]> {
    private Request request;
    private List<Byte> partedOutput;
    private File requestedFile;
    public Boolean recievedAnswer;
    private byte[] message;
    public TftpClientProtocol(){
        this.request = Request.None;
        this.partedOutput = new LinkedList<Byte>();
        this.requestedFile = null;
        this.message = null;
        this.recievedAnswer = false;
    }
    @Override
    public byte[] process(byte[] answer) {
        byte[] result = null;
        switch (answer[1]){
            case 3: //data packet
                if(request== Request.DIRQ){
                    for (int i = 6; i< answer.length;i++){
                        if(answer[i]==0){
                            System.out.println(String.valueOf(Util.convertListToArray(partedOutput)));
                            partedOutput.clear();
                        }
                        else partedOutput.add(answer[i]);
                    }
                }
                else if (request == Request.RRQ) {
                    try {
                        requestedFile.createNewFile(); // if it exists does nothing
                        BufferedWriter writer =
                                new BufferedWriter(new FileWriter(requestedFile.getAbsoluteFile()));
                        byte[] onlyData = Arrays.copyOfRange(answer,6,answer.length);
                        // Write content to the file
                        writer.write(Arrays.toString(onlyData));
                        // Ensure content is flushed to the file
                        writer.flush();
                        if (onlyData.length < 512) {
                            requestedFile.setReadable(true);
                            requestedFile.setReadOnly();
                            requestedFile = null;
                        }
                    } catch (IOException ignored) {}
                }
                if(answer.length< Util.MAX_PACKET_LENGTH+6) {
                    request = Request.None;
                    recievedAnswer = true;
                }
                result = new byte[]{0, 4, answer[4], answer[5]}; // ACK packet
                break;
            case 4: //ACK packet
                if (message == null) {
                    recievedAnswer = true;
                    break;
                }
                int currentPart = Util.byteHexArrayToInteger(new byte[]{answer[2],answer[3]}) + 1;
                byte[] currentMessage = Util.getPartArray(message, currentPart);
                if (Util.isLastPart(message, currentPart)) message = null;
                result = Util.createDataPacket(currentPart, currentMessage);
                break;
            case 5: //error packet
                System.out.println("ERROR " + answer[3] + Arrays.toString(
                        Arrays.copyOfRange(message,4,message.length-1)));
                recievedAnswer = true;
                break;
            case 9: //bcast
                String status = (answer[2]==1)? "add" : "del";
                System.out.println("BCAST" + status + Arrays.toString(Util.cutFromEnd(answer, 1)));
                break;
        }
        return result;
    }

    @Override
    public boolean shouldTerminate() {
        return false;
    }
    private enum Request{None, RRQ, DIRQ}
}
