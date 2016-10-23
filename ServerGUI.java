import javax.swing.*;
import java.util.*;
import java.awt.event.*;
import java.awt.Component;
import javax.swing.event.*;
import java.awt.BorderLayout;
import java.awt.GridLayout;
import javax.swing.border.EmptyBorder;
import java.io.*;

public class ServerGUI {
    private Server server;
    private List<ClientHolder> clients;
    private ClientHolder selected;
    private JFrame frame;
    private JList jlist;
    private DefaultListModel<ClientHolder> listmodel;
    private JLabel info;
    private JLabel pingTime, imageShowing, ipDisplay, idDisplay;
    private JButton displayImage, changeID, close;
    private static final String pingTimeStr = "Ping time: ";
    private static final String imageShowingStr = "Current image: ";
    private static final String ipDisplayStr = "IP Address: ";
    private static final String idDisplayStr = "ID: ";
    private static final String noClientsMsg = "No clients.";
    
    public ServerGUI(Server server, List<ClientHolder> clients) {
        this.server = server;
        this.clients = clients;
        
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                setLookAndFeel();
                initializeGUI();
            }
        }); //begin GUI
    }
    
    private void setLookAndFeel() {
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
    
    private void initializeGUI() {
        frame = new JFrame("Server");
        frame.setSize(600, 400);
        frame.setLocationRelativeTo(null);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        
        //setup main container
        JPanel main = new JPanel();
        main.setLayout(new BorderLayout());
        
        //setup jpanel for all elements besides scroller
        JPanel jpan = new JPanel();
        jpan.setLayout(new BoxLayout(jpan, BoxLayout.Y_AXIS));
        
        //make refresh button
        JButton refresh = new JButton("Refresh");
        refresh.setAlignmentX(Component.CENTER_ALIGNMENT);
        refresh.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent ae) {
                refreshJList();
            }
        });
        main.add(refresh, BorderLayout.NORTH);
        
        //create list
        listmodel = new DefaultListModel<ClientHolder>();
        JList<ClientHolder> jlist = new JList<ClientHolder>(listmodel);
        jlist.setSelectionMode(DefaultListSelectionModel.SINGLE_SELECTION);
        jlist.getSelectionModel().addListSelectionListener(
                                                           new SelectHandler());
        jlist.setAlignmentX(Component.CENTER_ALIGNMENT);
        jpan.add(jlist);
        
        //make this scrollable
        JPanel scrollpanel = new JPanel();
        JScrollPane jsp = new JScrollPane(scrollpanel);
        scrollpanel.setLayout(new BorderLayout());
        info = new JLabel(noClientsMsg); //info bar
        scrollpanel.add(jpan, BorderLayout.CENTER);
        scrollpanel.add(info, BorderLayout.SOUTH);
        main.add(jsp, BorderLayout.WEST);
        
        //create panel for displaying a screen's info
        JPanel display = new JPanel();
        display.setLayout(new BoxLayout(display, BoxLayout.Y_AXIS));
        close = new JButton("End connection");
        close.setEnabled(false);
        close.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent ae) {
                if(selected != null) {
                    selected.close();
                    refreshJList();
                } else {
                    noSelectionError();
                }
            }
        });
        pingTime = new JLabel(pingTimeStr + "...");
        imageShowing = new JLabel(imageShowingStr + "...");
        ipDisplay = new JLabel(ipDisplayStr + "...");
        idDisplay = new JLabel(idDisplayStr + "...");
        changeID = new JButton("Change ID");
        changeID.setEnabled(false);
        changeID.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent ae) {
                if(selected != null) {
                    String newid = JOptionPane.showInputDialog(frame, "Enter the desired ID. If the entered ID already exists, the two will swap.");
                    if(newid == null) {
                        return;
                    }
                    try {
                        int id = Integer.parseInt(newid);
                        if(id < 0) {
                            JOptionPane.showMessageDialog(frame, "ID must be >= 0.");
                        } else {
                            server.swapId(selected, id);
                            //finally, ID has been swapped
                            refreshJList();
                        }
                    } catch(NumberFormatException nfe) {
                        JOptionPane.showMessageDialog(frame, "Invalid integer.");
                    }
                } else {
                    noSelectionError();
                }
            }
        });
        displayImage = new JButton("Display image");
        displayImage.setEnabled(false);
        displayImage.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent ae) {
                if(selected != null) {
                    String toSend = JOptionPane.showInputDialog(frame, "Enter the filename to display.");
                    if(toSend == null) {
                        return;
                    }
                    selected.send(toSend);
                } else {
                    noSelectionError();
                }
            }
        });
        display.add(idDisplay);
        display.add(ipDisplay);
        display.add(imageShowing);
        display.add(pingTime);
        display.add(displayImage);
        display.add(changeID);
        display.add(close);
        //padding
        display.setBorder(new EmptyBorder(10, 10, 10, 10));
        main.add(display, BorderLayout.CENTER);
        
        //Send image
        final JButton sendImage = new JButton("Send image to clients");
        sendImage.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent ae) {
                JFileChooser fileChooser = new JFileChooser();
                if(fileChooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
                    File file = fileChooser.getSelectedFile();
                    //load from file
                    server.sendFileToClients(file);
                }
            }
        });
        
        //Script enable/disable button
        final JButton scriptEnable = new JButton("Start script (in.txt)");
        scriptEnable.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent ae) {
                server.toggleScript();
                if(server.isScriptEnabled()) {
                    scriptEnable.setText("Stop script");
                    sendImage.setEnabled(false);
                } else {
                    scriptEnable.setText("Start script");
                    sendImage.setEnabled(true);
                }
            }
        });
        
        //Bottom container with sendImage and scriptEnable buttons
        JPanel bottomPan = new JPanel();
        bottomPan.setLayout(new GridLayout(1, 2));
        bottomPan.add(sendImage);
        bottomPan.add(scriptEnable);
        main.add(bottomPan, BorderLayout.SOUTH);
        
        //add main
        frame.add(main);
        
        frame.setVisible(true);
    }
    
    private void noSelectionError() {
        displayError("Nothing selected.");
    }
    
    public void displayError(String error) {
        JOptionPane.showMessageDialog(frame, error);
    }
    
    //call to refresh display
    private void refreshDisplay() {
        if(selected == null) {
            //text
            pingTime.setText(pingTimeStr + "...");
            imageShowing.setText(imageShowingStr + "...");
            ipDisplay.setText(ipDisplayStr + "...");
            idDisplay.setText(idDisplayStr + "...");
            
            //buttons
            changeID.setEnabled(false);
            displayImage.setEnabled(false);
            close.setEnabled(false);
        } else {
            //text
            pingTime.setText(pingTimeStr + selected.getPingTime() + "ms");
            imageShowing.setText(imageShowingStr + selected.getCurrentImage());
            ipDisplay.setText(ipDisplayStr + selected.getIP());
            idDisplay.setText(idDisplayStr + selected.getId());
            
            //buttons
            changeID.setEnabled(true);
            displayImage.setEnabled(true);
            close.setEnabled(true);
        }
    }
    
    private class SelectHandler implements ListSelectionListener {
        public void valueChanged(ListSelectionEvent e) {
            ListSelectionModel lsm = (ListSelectionModel)e.getSource();
            if(lsm.getValueIsAdjusting()) {
                //this was generated by a real click
                selected = listmodel.getElementAt(lsm.getAnchorSelectionIndex());
                refreshDisplay();
            }
        }
    }
    
    private void refreshJList() {
        selected = null;
        listmodel.clear();
        if(clients.size() == 0) {
            info.setText(noClientsMsg);
        } else {
            info.setText("Found " + clients.size());
            for(ClientHolder ch : clients) {
                listmodel.add(listmodel.getSize(), ch);
            }
        }
        refreshDisplay();
    }
}