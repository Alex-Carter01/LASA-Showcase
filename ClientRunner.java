import javax.swing.*;
import javax.imageio.ImageIO;
import java.awt.event.*;
import java.awt.*;
import java.io.File;

public class ClientRunner {
    private static JLabel lab;
    private static JFrame frame;
    private static JCheckBox isAuto;
    private static int errorAttempts = 0;
    private static String ip;
    private static boolean debugMode;
    public static void main(final String[] args) {
        debugMode = false;
        if(args.length == 2 && args[1].equalsIgnoreCase("debug")) {
            System.out.println("----DEBUG MODE----");
            debugMode = true;
        } else {
            System.out.println("Use DEBUG as the 2nd argument for DEBUG mode. First is IP/host.");
        }
        
        //ensure that all the necesary files exist
        ensureExists("images/waiting.jpg");
        ensureExists("images/error.jpg");
        
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                setLookAndFeel();
                initializeGUI(args);
            }
        }); //begin GUI
    }
    
    private static void ensureExists(String filename) {
        File f = new File(filename);
        if(!f.exists() || f.isDirectory()) {
            JOptionPane.showMessageDialog(null, "File '" + filename + "' does not exist.");
            System.exit(6);
        }
    }
    
    public static void initializeGUI(final String[] args) {
        frame = new JFrame("Client");
        frame.setSize(350, 150);
        frame.setLocationRelativeTo(null);
        frame.setLayout(new GridBagLayout());
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        GridBagConstraints gbc = new GridBagConstraints();
        JPanel pan = new JPanel();
        pan.setLayout(new BoxLayout(pan, BoxLayout.Y_AXIS));
        //frame.add(Box.createVerticalGlue());
        isAuto = new JCheckBox("Automatic retry on fail");
        isAuto.setAlignmentX(Component.CENTER_ALIGNMENT);
        lab = new JLabel("STATUS: Initialized.", SwingConstants.CENTER);
        lab.setAlignmentX(Component.CENTER_ALIGNMENT);
        pan.add(isAuto);
        pan.add(lab);
        final JButton btn = new JButton("Start");
        btn.setAlignmentX(Component.CENTER_ALIGNMENT);
        pan.add(btn);
        //frame.add(Box.createVerticalGlue());
        frame.add(pan, gbc);
        //frame.pack();
        frame.setVisible(true);
        
        //the user is probably going to want
        //automatic mode
        isAuto.setSelected(true);
        
        //have to store item in box to access
        //from closure
        final Box<Boolean> hasInitialized = new Box<>(false);
        
        btn.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent ae) {
                if(hasInitialized.getValue()) {
                    //have initialized before
                    System.out.println("ActionEvent already initialized: strange.");
                    return;
                }
                hasInitialized.setValue(true);
                btn.setVisible(false);
                postInitialization(args);
            }
        });
        
        if(args.length != 0) {
            //automatically count down
            (new Thread(new Runnable() {
                public void run() {
                    //countdown button
                    for(int i = 0; i < 5; i++) {
                        btn.setText("Start (" + (5-i) + ")");
                        try {
                            if(!debugMode) {
                                Thread.sleep(1000);
                            }
                        } catch(InterruptedException ie) {
                            //nobody really cares
                        }
                    }
                    if(!hasInitialized.getValue()) {
                        //have not yet initialized
                        hasInitialized.setValue(true);
                        btn.setVisible(false);
                        postInitialization(args);
                    }
                }
            })).start();
        }
    }
    
    private static void postInitialization(String[] args) {
        getIP(args);
        final boolean debugModeFinal = debugMode;
        (new Thread(new Runnable() {
            public void run() {
                loop(debugModeFinal);
            }
        })).start();
    }
    
    private static void getIP(String[] args) {
        if(args.length == 0) {
            //prompt user for IP
            ip = JOptionPane.showInputDialog("Please enter the IP or hostname of the server.");
            //assume user here
            if(ip == null) {
                JOptionPane.showMessageDialog(frame, "That is not an IP.");
                System.exit(1);
            }
            lab.setText("Connecting...");
        } else {
            //we already have IP
            ip = args[0];
            //assume headless mode
        }
    }
    
    private static void setLookAndFeel() {
        try {
            // Set cross-platform Java L&F (also called "Metal")
            UIManager.setLookAndFeel(
                                     UIManager.getSystemLookAndFeelClassName());
        }
        catch (UnsupportedLookAndFeelException e) {
            //don't care
        }
        catch (ClassNotFoundException e) {
            //don't care
        }
        catch (InstantiationException e) {
            //don't care
        }
        catch (IllegalAccessException e) {
            //don't care
        }
    }
    
    //keep trying to make a connection with the server
    private static void loop(boolean debug) {
        while(true) {
            Client client = new Client();
            String res = client.init(ip, debug);
            if(res == null) {
                //client initialized successfully
                frame.setVisible(false); //hide
                lab.setText("ERROR"); //should never be shown
                
                String runresult = client.run(); //do client stuff
                client.exit();
                
                lab.setText("Please wait...");
                frame.setVisible(true);
                
                if(runresult != null) {
                    //error running
                    showError(runresult);
                    continue;
                }
                
                for(int i = 0; i < 3; i++) {
                    lab.setText("Success! Exiting in... " + (3-i));
                    try {
                        Thread.sleep(1000);
                    } catch(InterruptedException ie) {
                        //do nothing, doesn't really matter
                    }
                }
                System.out.println("Successful exit.");
                System.exit(0);
            } else {
                //client exited with an error
                showError(res);
            }
        }
    }
    //this will System.exit() under certain circumstances
    //if this method returns, it means try again
    public static void showError(String err) {
        if(isAuto.isSelected()) {
            //automatic mode
            System.err.println("ERROR: " + err);
            lab.setText(err);
            try {
                //try to reconnect to server in a little while, 15s
                Thread.sleep(15000);
            } catch(InterruptedException ie) {
                //don't really care
            }
            
            if(++errorAttempts >= 50) {
                //we've tried so hard
                //nobody cares
                
                //just shut it down
                System.err.println("Too many errors (" + errorAttempts + ").");
                JOptionPane.showMessageDialog(frame, "Too many errors (" + errorAttempts + "). Shutting down.");
                System.exit(1);
            }
        } else {
            //user controlled mode
            JOptionPane.showMessageDialog(frame, err);
            int reply = JOptionPane.showConfirmDialog(
                                                      null,
                                                      "Would you like to try again?",
                                                      "Try Again",
                                                      JOptionPane.YES_NO_OPTION);
            if(reply != JOptionPane.YES_OPTION) {
                //Just exit.
                System.exit(0);
            }
        }
    }
    
    private static class Box<T> {
        private T item;
        private final Object lock = new Object();
        public Box(T item) {
            this.item = item;
        }
        public void setValue(T item) {
            synchronized(lock) {
                this.item = item;
            }
        }
        public T getValue() {
            synchronized(lock) {
                return item;
            }
        }
    }
}