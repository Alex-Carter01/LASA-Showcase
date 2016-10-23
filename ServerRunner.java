import java.io.*;

public class ServerRunner {
    public static void main(String[] args) {
        Server server = new Server();
        try {
            server.start();
        } catch(IOException ioe) {
            ioe.printStackTrace();
        }
    }
}
