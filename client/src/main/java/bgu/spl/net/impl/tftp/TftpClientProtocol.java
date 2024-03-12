package bgu.spl.net.impl.tftp;

import bgu.spl.net.api.MessagingProtocol;
import bgu.spl.net.impl.Util;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
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
    public TftpClientProtocol(){
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
        switch (answer[1]){
            case 3: //data packet
                if(request == Util.OP.DIRQ){
                    System.out.println("files in dir:");
                    for (int i = 6; i< answer.length;i++){
                        if(answer[i]==0){
                            System.out.println(new String(Util.convertListToArray(partedOutput)));
                            partedOutput.clear();
                        }
                        else partedOutput.add(answer[i]);
                    }
                    if (answer.length>6 & answer.length<Util.MAX_PACKET_LENGTH + 6) {
                        System.out.println(new String(Util.convertListToArray(partedOutput)));
                        partedOutput.clear();
                    }
                }
                else if (request == Util.OP.RRQ) {
                    try {
                        if (!requestedFile.exists()){
                            requestedFile.createNewFile(); // if it exists does nothing
                            requestedFile.setReadable(false);
                        }
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
                            System.out.println(requestedFile.getName() + " added succesfully");
                            requestedFile = null;
                        }
                    } catch (IOException ignored) {}
                }
                if(answer.length< Util.MAX_PACKET_LENGTH+6) {
                    request = Util.OP.None;
                    recievedAnswer = true;
                }
                else result = new byte[]{0, 4, answer[4], answer[5]}; // ACK packet
                break;
            case 4: //ACK packet
                if (messageToSend == null) {
                    recievedAnswer = true;
                    if (request == Util.OP.DISC) terminate=true;
                    else if(request == Util.OP.WRQ) System.out.println("finished uploading file");
                    else if(request == Util.OP.LOGRQ) System.out.println("logged in succesfully");
                    request = Util.OP.None;
                    break;
                }
                short currentPart = (short)(Util.byteHexArrayToShort(new byte[]{answer[2],answer[3]}) + 1);
                byte[] currentMessage = Util.getPartArray(messageToSend, currentPart);
                if (Util.isLastPart(messageToSend, currentPart)) messageToSend = null;
                result = Util.createDataPacket(currentPart, currentMessage);
                break;
            case 5: //error packet
                System.out.println("ERROR " + answer[3] + ": " + new String(
                        Arrays.copyOfRange(answer,4,answer.length-1)));
                recievedAnswer = true;
                break;
            case 9: //bcast
                String status = (answer[2]==1)? "add" : "del";
                System.out.println("BCAST" + status + new String(Util.cutFromEnd(answer, 1)));
                break;
        }
        return result;
    }

    @Override
    public boolean shouldTerminate() {
        return terminate;
    }

    public void inform(Util.OP request){
        this.request = request;
    }
    public void inform(String nameOfFileRequested){
        requestedFile = Util.getFile(nameOfFileRequested);
        if(requestedFile.exists()) requestedFile.delete();
    }

}
