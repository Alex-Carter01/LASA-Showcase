import java.io.*;
import java.net.*;
import java.util.*;

public class ClientHolder implements Runnable {
    private int id;
    private Socket sock;
    private boolean clientActive = false;
    private Server server;
    private String currentImage = "waiting.jpg";
    private long pingTime = 0;
    private DataOutputStream outData;
    private boolean isSendingImage = false;
    private BufferedReader inFromClient;
    
    public ClientHolder(Socket s, int id, Server server) {
        sock = s;
        this.id = id;
        this.server = server;
    }
    public int getId() {
        return id;
    }
    public void setId(int id) {
        this.id = id;
    }
    public String getIP() {
        return sock.getRemoteSocketAddress().toString();
    }
    public long getPingTime() {
        return pingTime;
    }
    public String getCurrentImage() {
        return currentImage;
    }
    public void close() {
        try {
            sock.close();
        } catch(IOException ioe) {
            ioe.printStackTrace();
        }
    }
    @Override
    public String toString() {
        return "Client ID " + id;
    }
    private void print(String s) {
        System.out.println("ID " + id + " " + s);
    }
    public void error(String s) {
        server.error("Client " + id + ": " + s);
    }
    private boolean ensureActive() {
        if(!clientActive) {
            server.removeFromList(this);
            print("dead client.");
        }
        return clientActive;
    }
    public void setIsSendingImage(boolean b) {
        isSendingImage = b;
    }
    public boolean send(String s) {
        if(!ensureActive()) return false;
        if(isSendingImage) {
            print("Warning: message '" + s + "' not sent.");
            return false;
        }
        
        print("sending: " + s);
        try {
            outData.writeBytes(s + '\n');
            outData.flush();
        } catch(IOException ioe) {
            failedTransfer(ioe);
        }
        return true;
    }
    public void send(byte[] bytes, int offset, int len) {
        if(!ensureActive()) return;
        //print("sending binary");
        //System.out.println(len);
        //System.out.println(new String(bytes, "ASCII"));
        try {
            outData.write(bytes, offset, len);
            flush();
        } catch(IOException ioe) {
            failedTransfer(ioe);
        }
    }
    private void failedTransfer(IOException ioe) {
        print("Failed transfer. Marking dead.");
        ioe.printStackTrace();
        clientActive = false;
    }
    public String receiveLine() {
        if(!ensureActive()) return null;
        
        try {
            return inFromClient.readLine();
        } catch(IOException ioe) {
            failedTransfer(ioe);
            return null;
        }
    }
    public void flush() {
        try {
            outData.flush();
        } catch(IOException ioe) {
            failedTransfer(ioe);
        }
    }
    //Initialize & handshake with client
    public void run() {
        try {
            inFromClient =
            new BufferedReader(new InputStreamReader(sock.getInputStream()));
            String clientSentence = inFromClient.readLine();
            print("received: " + clientSentence);
            if(clientSentence.equals("BEGIN")) {
                print("ESTABLISHED CONNECTION.");
                clientActive = true;
                outData = new DataOutputStream(sock.getOutputStream());
                outData.writeBytes("0" + '\n');
                outData.flush();
                print("sent 0");
                while(true) {
                    //do periodic pinging of client
                    
                    //wait 10 seconds between pings
                    try {
                        Thread.sleep(10000);
                    } catch(InterruptedException ioe) {}
                    
                    //wait to ping later if image transfer is occuring
                    if(isSendingImage) {
                        try {
                            print("Delaying ping because of image being sent.");
                            Thread.sleep(1000);
                        } catch(InterruptedException ioe) {}
                    }
                    
                    long pingStart = System.currentTimeMillis();
                    try {
                        outData.writeBytes("." + '\n');
                        outData.flush();
                        
                        //expect dot back
                        String input = inFromClient.readLine();
                        if(input == null) {
                            //we're dead, kill it off
                            server.removeFromList(this);
                            return;
                        } else {
                            if(input.equals(".")) {
                                //success
                                pingTime = System.currentTimeMillis() - pingStart;
                            } else if(input.length() > 8 && input.substring(0, 8).equals("inv_img:")) {
                                String image = input.substring(8);
                                error("Cannot display image: '" + image + "'. Is the filename correct?");
                            } else {
                                //we're getting erroneous input
                                error("Unexpected input: " + input);
                                server.removeFromList(this);
                                return;
                            }
                        }
                    } catch(IOException ioe) {
                        print("Ping failed.");
                        server.removeFromList(this);
                        return;
                    }
                }
                //out.close();
            } else {
                print("DID NOT RECEIVE \"START\".");
            }
            inFromClient.close();
        } catch(IOException ioee) {
            print("TRY FAILED.");
            print("IOException: clientholder first try");
            ioee.printStackTrace();
        }// finally {
        //	try {
        //		sock.close();
        //	} catch(IOException ioe) {
        //		System.out.println("Couldn't even close the socket.");
        //	}
        //}
    }
    
}