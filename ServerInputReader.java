import java.io.*;
import java.net.*;
import java.util.*;

public class ServerInputReader implements Runnable {
	List<ClientHolder> list;
	public ServerInputReader(List<ClientHolder> list) {
		this.list = list;
	}
	public void run_userinput() {
		Scanner scan = new Scanner(System.in);
		while(true) {
			String s = scan.nextLine();
			int id = -1;
			String[] split = s.split(" ");
			if(split.length > 1) {
				try {
					id = Integer.parseInt(split[0]);
				} catch(NumberFormatException nfe) {
					System.out.println("Please use the form <filename> OR <id> <filename>.");
					id = -1;
				}
			}
			for(ClientHolder item : list) {
				if(id == -1) { //send to all
					item.send(s);
				} else {
					if(item.getId() == id) {
						item.send(split[1]);
					}
				}
			}
		}
	}
	public void run() {
		//System.out.println("Press enter to begin.");
		//Scanner scanA = new Scanner(System.in);
		//scanA.nextLine();
		//scanA.close();
		try {
			while(true) {
                //don't overstay our welcome
                if(Thread.interrupted()) {
                    return;
                }
                Scanner scan = new Scanner(new File("in.txt"));
                while(scan.hasNextLine()) {
                    try {
                        String s = scan.nextLine();
                        try {
                            int delay = Integer.parseInt(s);
                            Thread.sleep(delay);
                            continue;
                        } catch(NumberFormatException nfe) {
                            //just not a delay, ignore
                        } catch(InterruptedException ie) {
                            //interrupted, let's return
                            scan.close();
                            return;
                        }
                        int id = -1;
                        int spaceInd = s.indexOf(" ");
                        if(spaceInd != -1) {
                            try {
                                id = Integer.parseInt(s.substring(0, spaceInd));
                            } catch(NumberFormatException nfe) {
                                //System.out.println("Please use the form <filename> OR <id> <filename>.");
                                id = -1;
                            }
                        }
                        String toSend = s.substring(spaceInd+1);
                        for(ClientHolder item : list) {
                            if(id == -1) { //send to all
                                item.send(s);
                            } else {
                                if(item.getId() == id) {
                                    item.send(toSend);
                                }
                            }
                        }
                    } catch(ConcurrentModificationException cme) {
                        //we tried to read from a list while it was being
                        //modified, just keep looping. it doesn't matter
                        System.out.println("Concurrent modification occured.");
                    }
                }
                scan.close();
			}
		} catch(FileNotFoundException fnfe) {
			System.out.println("in.txt NOT FOUND");
			System.exit(4);
		}
	}
}