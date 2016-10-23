import java.io.*;
import java.net.*;
import java.util.*;
import javax.swing.*;

public class Server implements Runnable
{
    public List<ClientHolder> list = Collections.synchronizedList(new ArrayList<ClientHolder>());
    private boolean isScriptEnabled = false;
    private Thread scriptThread;
    private ServerGUI gui;
    
    public synchronized void removeFromList(ClientHolder ch) {
        list.remove(ch);
    }
    public void error(String error) {
        gui.displayError(error);
    }
    public void toggleScript() {
        if(isScriptEnabled) {
            disableScript();
        } else {
            enableScript();
        }
        isScriptEnabled = !isScriptEnabled;
    }
    public boolean isScriptEnabled() {
        return isScriptEnabled;
    }
    public void disableScript() {
        if(scriptThread != null) {
            scriptThread.interrupt();
            scriptThread = null;
        } else {
            System.err.println("Script thread should not be null.");
        }
    }
    public void enableScript() {
        if(scriptThread != null) {
            System.err.println("Script thread should be null.");
            return;
        }
        scriptThread = new Thread(new ServerInputReader(list));
        scriptThread.start();
    }
    public void start() throws IOException
    {
        System.out.println("Starting up...");
        gui = new ServerGUI(Server.this, list); //begin GUI
        (new Thread(this)).start(); //wait for connections
    }
    
    //swaps the variable 'selected' with the
    //item at ID
    public void swapId(ClientHolder toSwap, int id) {
        //locate item to swap with
        ClientHolder ch = null;
        for(ClientHolder item : list) {
            if(item.getId() == id) {
                ch = item;
            }
        }
        
        //if we found something to swap, set its
        //id to our id
        if(ch != null) {
            ch.setId(toSwap.getId());
        }
        
        //now we set our id to the new id
        toSwap.setId(id);
    }
    
    //sends an image file to all of the clients
    public void sendFileToClients(File f) {
        if(list.size() == 0) return;
        
        //alert clients that they will receive an image
        sendStringToClients("imagesize:" + f.length());
        sendStringToClients("image:" + f.getName());
        setClientsReadyToReceiveImage(true);
        flushClients();
        
        for(ClientHolder ch : list) {
            String line = ch.receiveLine();
            if(line != null && !line.equals("READY")) {
                ch.error("unable to receive image.");
            }
        }
        
        int buffersize = 1024;
        int offset = 0;
        byte[] fileData = new byte[buffersize];
        
        int numBytesRead;
        String string;
        try {
            DataInputStream in = new DataInputStream(new FileInputStream(f));
            while((numBytesRead = in.read(fileData, 0, buffersize)) != -1)
            {
                //System.out.println(numBytesRead);
                //sendStringToClients("!");
                sendBinaryToClients(fileData, 0, numBytesRead);
            }
            flushClients();
            in.close();
        } catch(IOException ioe) {
            gui.displayError("IOException during file read: " + f);
        }
        setClientsReadyToReceiveImage(false);
    }
    
    private void flushClients() {
        for(ClientHolder ch : list) {
            ch.flush();
        }
    }
    
    private void setClientsReadyToReceiveImage(boolean b) {
        for(ClientHolder ch: list) {
            ch.setIsSendingImage(b);
        }
    }
    
    private void sendStringToClients(String s) {
        for(ClientHolder ch : list) {
            ch.send(s);
        }
    }
    
    private void sendBinaryToClients(byte[] bytes, int offset, int len) {
        for(ClientHolder ch : list) {
            ch.send(bytes, offset, len);
        }
    }
    
    public void run() {
        try {
            String clientSentence;
            String capitalizedSentence;
            ServerSocket welcomeSocket = new ServerSocket(6789);
            System.out.println("Created socket.");
            int nextId = 0;
            while(true)
            {
                System.out.println("Waiting for connection...");
                Socket connectionSocket = welcomeSocket.accept();
                System.out.println("Received connection.");
                ClientHolder ch = new ClientHolder(connectionSocket, nextId++, this);
                (new Thread(ch)).start(); //give it its own thread where it pings and listens for data
                list.add(ch);
            }
        } catch(IOException ioe) {
            System.err.println("IOException");
            ioe.printStackTrace();
        }
    }
}
