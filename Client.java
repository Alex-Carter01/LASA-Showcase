import java.io.*;
import java.net.*;
import java.awt.*;
import java.awt.image.*;
import javax.swing.*;
import javax.imageio.ImageIO;
import java.awt.geom.Rectangle2D;

public class Client
{
    private JFrame frame;
    private BufferedImage img;
    private Socket clientSocket;
    private boolean windowedMode;
    private DataOutputStream outToServer;
    private boolean isAnimating = false;
    private Rectangle2D.Float animationRect;
    private Rectangle2D.Float animationSpeed;
    private int animationCycles; //animation requirement
    private int animationCycleNum;
    private Thread animationThread;
    private BufferedImage animationImage;
    //Return int is exit information. Null on success.
    public String init(String ip, boolean windowedMode)
    {
        this.windowedMode = windowedMode;
        if(ip.length() == 0) {
            return "Specify an IP as the argument.";
        }
        try {
            clientSocket = new Socket(ip, 6789);
            //ANY OF THE NEXT THREE ERRORS HAPPENED DURING
            //CONSTRUCTION, SO WE DON'T HAVE TO CLOSE
            return null;
        } catch(ConnectException ce) {
            ce.printStackTrace();
            return "Unable to connect. Are you sure that is the right IP?";
        } catch(UnknownHostException uhe) {
            uhe.printStackTrace();
            return "Unknown host.";
        } catch(IOException ioe) {
            ioe.printStackTrace();
            return "Unable to connect to host: IOException.";
        }
    }
    public void exit() {
        if(clientSocket.isClosed() == false) {
            try {
                clientSocket.close();
            } catch(IOException ioe) {
                ioe.printStackTrace();
            }
        }
        frame.dispose();
    }
    //Return exit information. Null on success.
    public String run() {
        try {
            processInput(clientSocket);
            clientSocket.close();
            return null;
        } catch(IOException ioe) {
            ioe.printStackTrace();
            try {
                clientSocket.close();
            } catch(IOException a) {}
            return "IOException. Did the server terminate?";
        }
    }
    public void processInput(Socket clientSocket) throws IOException {
        outToServer = new DataOutputStream(clientSocket.getOutputStream());
        outToServer.writeBytes("BEGIN" + '\n');
        DataInputStream dis = new DataInputStream(clientSocket.getInputStream());
        BufferedReader inFromServer = new BufferedReader(new InputStreamReader(dis));
        while(true) {
            String in = inFromServer.readLine();
            System.out.println("FROM SERVER: " + in);
            if(in == null) {
                System.out.println("Server terminated connection. Exiting...");
                return;
            } else if(in.equals("END")) {
                System.out.println("Received END");
                break;
            } else if(in.equals("0")) {
                createFrame();
            } else if(in.equals(".")) {
                //ping. respond back
                outToServer.writeBytes(".\n");
            } else if(in.indexOf(":") != -1) {
                //we're getting a command
                handleCommand(in, dis, System.currentTimeMillis());
            } else {
                setImage(in);
            }
            //make sure NOTHING goes here
        }
        outToServer.close();
        inFromServer.close();
        dis.close();
        //socket closes automatically after here
    }
    //extract rect from (x:width|y:height)
    private Rectangle extractRectangleFromString(String s) {
        s = s.trim();
        if(s.charAt(0) != '(' || s.charAt(s.length()-1) != ')') {
            //isn't of form (...)
            return null;
        }
        s = s.substring(1, s.length()-1);
        //now we have x:width, y:height
        String[] xandy = s.split(",");
        //this is [x:width], [y:height]
        if(xandy.length != 2) {
            return null;
        }
        
        int[][] xandySplit = new int[2][2];
        for(int i = 0; i < xandy.length; i++) {
            String[] values = xandy[i].split(":");
            if(values.length != 2) {
                //isn't of form a:b
                return null;
            }
            try {
                int a = Integer.parseInt(values[0].trim());
                int b = Integer.parseInt(values[1].trim());
                
                xandySplit[i][0] = a;
                xandySplit[i][1] = b;
            } catch(NumberFormatException nfe) {
                return null;
            }
        }
        
        //xandySplit is now:
        //[x, width], [y, height]
        
        Rectangle rect = new Rectangle(xandySplit[0][0],
                                       xandySplit[1][0],
                                       xandySplit[0][1],
                                       xandySplit[1][1]);
        return rect;
    }
    private String imageFilename;
    private boolean isReceivingImage = false;
    private DataOutputStream imageOut = null;
    private long fileSize = 0;
    private void handleCommand(String in, DataInputStream dis, long timeBegin) {
        //handle command in the form of command:argument
        int colonLoc = in.indexOf(":");
        String commandName = in.substring(0, colonLoc);
        String commandContent = in.substring(colonLoc + 1);
        
        //check if command is image:...
        if(commandName.equals("image")) {
            endImageOutput(); //make sure we're still not writing to an old file
            //split[1] is filename
            isReceivingImage = true;
            imageFilename = commandContent;
            
            String filePath = "images/" + imageFilename;
            try {
                File f = new File(filePath);
                f.createNewFile();
                imageOut = new DataOutputStream(new FileOutputStream(f, false));
                outToServer.writeBytes("READY" + '\n');
                handleImageInput(dis);
            } catch(FileNotFoundException fnfe) {
                System.out.println("File not found: " + filePath);
                endImageOutput();
            } catch(IOException ioe) {
                System.out.println("IOException: " + filePath);
                ioe.printStackTrace();
                endImageOutput();
            }
        } else if(commandName.equals("imagesize")) {
            //imagemeta:end ends image stream
            try {
                fileSize = Long.parseLong(commandContent);
                System.out.println("File size: " + fileSize);
            } catch(NumberFormatException nfe) {
                System.err.println("Unable to determine file size.");
            }
        } else if(commandName.equals("animate")) {
            setupAnimation(commandContent, timeBegin);
        } else {
            System.err.println("Unrecognized command: '" + commandName + "'. Continuing anyway.");
        }
    }
    private void displayErrorImage() {
        setImage("error.jpg");
    }
    private enum AnimationType {
        FILL, //grab portion of image and make fill
        CROP, //crop section of image
        LOCATION, //move image to location
        NONE
    }
    private void setupAnimation(String str, long timeBegin) {
        if(isAnimating) {
            //we're already animating
            if(animationThread != null) {
                animationThread.interrupt();
            }
        }
        
        isAnimating = true;
        
        //we want to animate
        //syntax of command: animate:filename, (startx:startwidth, starty:startheight), (endx:endwidth, endy:endheight), mode, time, [clearBuffer]
        String[] args = new String[5];
        
        //because some of the args contain commas themselves, we have to expect
        //6 and then we'll tone it down to the real array
        String[] argsTemp = str.split(",");
        if(argsTemp.length < 7 || argsTemp.length > 8) {
            System.err.println("Invalid animate command: requires 4-5 args.");
            //System.out.println(args.length);
            return;
        }
        args[0] = argsTemp[0];
        args[1] = argsTemp[1] + "," + argsTemp[2];
        args[2] = argsTemp[3] + "," + argsTemp[4];
        args[3] = argsTemp[5];
        args[4] = argsTemp[6];
        
        //get mode
        AnimationType modeTemp = AnimationType.NONE;
        args[3] = args[3].trim();
        if(args[3].equalsIgnoreCase("fill")) {
            modeTemp = AnimationType.FILL;
        } else if(args[3].equalsIgnoreCase("CROP")) {
            modeTemp = AnimationType.CROP;
        } else if(args[3].equalsIgnoreCase("LOCATION")) {
            modeTemp = AnimationType.LOCATION;
        } else {
            System.err.println("Invalid animation type: " + args[3] + ".");
            return;
        }
        final AnimationType mode = modeTemp; //so can be accessed from inner class
        
        //if last argument is not true, set this to false, else true
        final boolean clearBuffer = (argsTemp.length == 8 ?
                                     argsTemp[7].equalsIgnoreCase("true") :
                                     true);
        
        //fetch image
        animationImage = setImageHelper(args[0].trim());
        if(animationImage == null) {
            displayErrorImage();
            return;
        }
        
        Rectangle initialRect = extractRectangleFromString(args[1]);
        Rectangle finalRect = extractRectangleFromString(args[2]);
        
        if(initialRect != null && finalRect != null) {
            isAnimating = true;
        } else {
            System.err.println("One or more of the animation rectangles have invalid syntax. Initial rect: " + initialRect + " final rect: " + finalRect + ".");
            return;
        }
        
        int timeRequired = 0;
        try {
            timeRequired = Integer.parseInt(args[4].trim());
            if(timeRequired < 1) {
                System.err.println("Time required cannot be negative or zero.");
                return;
            }
        } catch(NumberFormatException nfe) {
            System.err.println("Time required is not a number.");
            return;
        }
        
        long timeUsedByInit = System.currentTimeMillis() - timeBegin;
        //System.out.println("Took " + timeUsedByInit + " ms to init.");
        timeRequired -= (int)timeUsedByInit;
        if(timeRequired <= 0) {
            return;
        }
        
        //nice, we're ready to start animation.
        //get some constants going
        //cycle = animation frame, btw
        final float FPS = 30;
        final float timePerCycle = (int)(1000/FPS); //round down
        
        //how many cycles we'll have to use
        final float numberOfCycles = timeRequired/timePerCycle;
        animationCycles = (int)numberOfCycles;
        animationCycleNum = 0; //curent cycle index
        
        //figure out distance to each point for calculations
        final int distXtoX = finalRect.x      - initialRect.x;
        final int distYtoY = finalRect.y      - initialRect.y;
        final int distWtoW = finalRect.width  - initialRect.width;
        final int distHtoH = finalRect.height - initialRect.height;
        
        //each of x, y, width, and height are not locations in
        //the rectangle animationSpeed, but are the rate at which
        //they should change
        animationSpeed = new Rectangle2D.Float(
                                               distXtoX/numberOfCycles,
                                               distYtoY/numberOfCycles,
                                               distWtoW/numberOfCycles,
                                               distHtoH/numberOfCycles
                                               );
        
        //this holds the current location of the viewport
        animationRect = new Rectangle2D.Float(
                                              (float)initialRect.x,
                                              (float)initialRect.y,
                                              (float)initialRect.width,
                                              (float)initialRect.height
                                              );
        
        //ensure that the new image is the right size, and hardware
        //accelerated
        final GraphicsEnvironment env = GraphicsEnvironment.getLocalGraphicsEnvironment();
        final GraphicsDevice device = env.getDefaultScreenDevice();
        final GraphicsConfiguration config = device.getDefaultConfiguration();
        BufferedImage newImg = config.createCompatibleImage(animationImage.getWidth(), animationImage.getHeight());
        if(!clearBuffer) {
            //draw old image back
            Graphics g = newImg.getGraphics();
            g.drawImage(img, 0, 0, animationImage.getWidth(), animationImage.getHeight(), null);
            g.dispose();
        }
        img = newImg;
        
        final int timeRequiredFinal = timeRequired; //in order to access below
        
        //updates img and refreshes display
        animationThread = new Thread(new Runnable() {
            public void run() {
                Graphics g = null;
                g = img.getGraphics();
                g.setColor(Color.BLACK);
                final int maxX = img.getWidth();
                final int maxY = img.getHeight();
                int sleepTime = (int)timePerCycle;
                long previousTime = System.currentTimeMillis();
                long endTime = System.currentTimeMillis() + (long)(sleepTime * numberOfCycles);
                final Rectangle bounds = new Rectangle(0, 0, maxX, maxY);
                BufferedImage buffer = null;
                Graphics bufferGraphics = null;
                if(mode == AnimationType.FILL) {
                    buffer = config.createCompatibleImage(maxX, maxY);
                    bufferGraphics = buffer.getGraphics();
                    bufferGraphics.setColor(Color.BLACK);
                }
                while(!Thread.interrupted()) {
                    int x = (int)(animationRect.x + animationSpeed.x * animationCycleNum);
                    int y = (int)(animationRect.y + animationSpeed.y * animationCycleNum);
                    int w = (int)(animationRect.width + animationSpeed.width * animationCycleNum);
                    int h = (int)(animationRect.height + animationSpeed.height * animationCycleNum);
                    
                    switch(mode) {
                        case CROP:
                            //crop to rectangle
                            if(clearBuffer) g.fillRect(0, 0, img.getWidth(), img.getHeight());
                            g.drawImage(animationImage.getSubimage(x, y, w, h),
                                        x,
                                        y,
                                        null);
                            break;
                        case FILL:
                            //fill image with rectangle
                            if(x < 0 || y < 0 || x+w > maxX || y+h > maxY) {
                                bufferGraphics.fillRect(0, 0, maxX, maxY);
                                
                                int drawX = 0;
                                int drawY = 0;
                                int drawW = maxX;
                                int drawH = maxY;
                                
                                if(x < 0) {
                                    drawX = (int)(((-(float)x)/w)*maxX);
                                    x = 0;
                                }
                                
                                if(y < 0) {
                                    drawY = (int)(((-(float)y)/h)*maxY);
                                    y = 0;
                                }
                                
                                int newW = w;
                                if(x+w >= maxX) {
                                    newW = maxX - x;
                                    drawW = (int)((((float)newW)/w)*maxX);
                                }
                                
                                int newH = h;
                                if(y+h >= maxY) {
                                    newH = maxY - y;
                                    drawH = (int)((((float)newH)/h)*maxY);
                                }
                                
                                if(x < maxX && y < maxY) bufferGraphics.drawImage(animationImage.getSubimage(x, y, newW, newH), drawX, drawY, drawW, drawH, null);
                                
                                img = buffer;
                            } else {
                                img = animationImage.getSubimage(x, y, w, h);
                            }
                            break;
                        case LOCATION:
                            //draw image at location
                            if(clearBuffer) g.fillRect(0, 0, img.getWidth(), img.getHeight());
                            g.drawImage(animationImage, x, y, w, h, null);
                            break;
                    }
                    
                    frame.repaint();
                    long timeLeft = endTime-previousTime;
                    float percentComplete = 1-((float)timeLeft)/((float)timeRequiredFinal);
                    animationCycleNum = 1 + (int)(percentComplete * (int)numberOfCycles);
                    //System.out.printf("%%%f of %f: %d\n", percentComplete*100, numberOfCycles, animationCycleNum);
                    if(animationCycleNum > animationCycles) {
                        //animation over
                        System.out.println("Completed animation");
                        isAnimating = false;
                        break;
                    }
                    
                    try {
                        long timeDeficit = System.currentTimeMillis() - previousTime;
                        long toSleep = sleepTime - timeDeficit;
                        if(toSleep > 0) {
                            Thread.sleep(toSleep);
                        } else {
                            System.err.println("Can't keep up with animation!");
                        }
                        previousTime = System.currentTimeMillis();
                    } catch(InterruptedException ie) {
                        System.out.println("Ending animation thread");
                        break;
                    }
                }
                if(g != null) {
                    g.dispose();
                }
                if(bufferGraphics != null) {
                    bufferGraphics.dispose();
                }
            }
        });
        
        animationThread.start();
    }
    private void endImageOutput() {
        if(imageOut != null) {
            //still writing to some other file
            System.err.println("Ending image output.");
            try {
                imageOut.flush();
                imageOut.close();
            } catch(IOException ioe) {
                System.err.println("Unable to close image output stream.");
                ioe.printStackTrace();
            }
            imageOut = null;
            fileSize = 0;
        }
        isReceivingImage = false;
    }
    private static final int toReadSize = 1024;
    private void handleImageInput(DataInputStream dis) {
        if(!isReceivingImage) {
            //shouldn't be getting data
            System.err.println("Unexpected image data.");
            return;
        }
        //load image data from dis into file
        byte[] section = new byte[toReadSize];
        long totalBytesRead = 0;
        try {
            while(isReceivingImage) {
                System.out.print("Receiving image");
                int bytesRead = dis.read(section, 0, toReadSize);
                totalBytesRead += bytesRead;
                imageOut.write(section, 0, bytesRead);
                
                byte[] newbyte = java.util.Arrays.copyOfRange(section, 0, bytesRead);
                //System.out.println("Read: " + new String(newbyte, "ASCII"));
                
                if(totalBytesRead >= fileSize || bytesRead < toReadSize) {
                    System.out.println(", read all of file.");
                    System.out.println("Total bytes: " + totalBytesRead + " out of: " + fileSize + ", read this time: " + bytesRead);
                    endImageOutput();
                    return;
                } else {
                    System.out.println(", read " + bytesRead + " of binary.");
                }
            }
        } catch(IOException ioe) {
            ioe.printStackTrace();
            endImageOutput();
        }
    }
    public void refreshFrame() {
        //frame.revalidate();
        frame.repaint();
    }
    private BufferedImage setImageHelper(String filename) {
        try {
            //frame.getContentPane().removeAll();
            return ImageIO.read(new File("images/" + filename));
        } catch(IOException ioe) {
            //alert server that this image does not exist
            try {
                outToServer.writeBytes("inv_img:" + filename + '\n');
            } catch(IOException ioe2) {
                System.err.println("Unable to report image error.");
                ioe2.printStackTrace();
            }
        }
        return null;
    }
    public void setImage(String filename) {
        if(animationThread != null) {
            //tell animation to finish and wait
            try {
                animationThread.interrupt();
                animationThread.join();
            } catch(InterruptedException ie) {
                ie.printStackTrace();
            }
        }
        
        BufferedImage tmp = setImageHelper(filename);
        if(tmp == null) {
            System.err.println("Falling back to error image");
            displayErrorImage();
        }
        img = tmp;
        //frame.add(new Component() {
        //    @Override
        //    public void paint(Graphics g) {
        //        super.paint(g);
        //        g.drawImage(img, 0, 0, getWidth(), getHeight(), this);
        //    }
        //});
        refreshFrame();
    }
    private boolean frameInitialized = false;
    public void createFrame() throws IOException {
        if(frameInitialized) {
            //already happened
            System.out.println("Frame already initialized.");
            return;
        }
        frameInitialized = true;
        
        frame = new JFrame("TEST");
        if(!windowedMode) {
            frame.setUndecorated(true);
        } else {
            frame.setSize(320, 180);
        }
        setImage("waiting.jpg");
        frame.add(new Component() {
            @Override
            public void paint(Graphics g) {
                super.paint(g);
                g.drawImage(img, 0, 0, getWidth(), getHeight(), this);
            }
        });
        frame.revalidate();
        
        if(!windowedMode) {
            GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
            GraphicsDevice gs = ge.getDefaultScreenDevice();
            gs.setFullScreenWindow(frame);
        } else {
            frame.setVisible(true);
        }
        
        frame.validate();
    }
}