# LASA-Showcase
Java TCP server/client for displaying images &amp; animating across multiple computers over the network. Created for the LASA showcase.

WARNING: animation performance is not hardware accelerated & kinda iffy. Use with caution.

## Purpose
The goal of this is to allow multiple computers to be connected together and controlled from a central server. This server can tell each of the clients to display images, animations, etc. It was created for the LASA 2016 showcase. It will be installed on all of the computers connected to the projectors outside of the building, so that synced animations can be shown.

## Usage
First, compile the Java files for both the server and client (`javac *.java`).
Next, start up the server. Run `server.sh` or `server.bat` depending on your OS. A window will pop up.

After that, go to each of your client computers and run `client.sh` or `client.bat`. Then, click the Start button and enter the IP of the server. Additionally, if you want more precise control, running `java ClientRunner host` will start the client and automatically connect to `host` instead of requiring input by the GUI. Lastly, to enter windowed mode and skip the GUI altogether, you can run `java ClientRunner host debug`. Now you can run multiple test monitors on one machine. This should not be used for any reason other than debugging your `in.txt` script.

Next, clicking the Refresh button in the server's window will show all of your clients connected and their ID. You can modify and disconnect clients, as well as sending them images to be displayed. To automate displaying images (when this program actually becomes useful), see scripting below.

## Scripting
Your script `in.txt` lets you define what you want to happen across the monitors. Let's start with an example. Assume I have three monitors. When you connect all three clients to your server and press refresh, you should see something like this:
![Screenshot of server](https://s18.postimg.org/5ruqjh4h3/Screen_Shot_2016_10_22_at_8_19_25_ptm.png)
