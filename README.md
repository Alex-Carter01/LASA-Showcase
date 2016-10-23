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
Notice how there are IDs 0, 1, and 2.

Right now, all of the three computers will be displaying `images/waiting.jpg`. Note that `images/waiting.jpg` and `images/error.jpg` are required for the program to run.

Now let's say we wanted to display `A.png`, `B.png`, and `C.png` across the three computers, so that it looked like this:
![Three monitors, the first of which says A, second B, and third C](https://s18.postimg.org/i8pwq6daf/abc.png)
We would first place all three images in the `images` directory of each of the clients (use the send image button to send from server to clients instead of manually placing if desired). Then, we would change in.txt to:
```
0 A.png
1 B.png
2 C.png
10000
```
Now, clicking the `Start script` button on the server computer will make the computers display like in the picture.

Let's break down the `in.txt` above. The first three lines are self explanatory: display `A.png` on computer 0, `B.png` on 1, etc. The `10000` sleeps for 10 seconds (`10000` is 10s in ms). It is there because this system is designed to animate looping designs, and so as soon as EOF is reached, the server starts again. To prevent the sevrer from constantly sending images, we wait 10 seconds between sending the images, and looping again.

If you don't want to loop, and just want still images, you could either hit the `Stop script` button, or just use the `Display image` button on the server to display an image manually on each of the clients.

Right now, the script is pretty useless, because it doesn't actually animate anything. This could be done with static images. So, let's spice it up. Send a `blank.jpg` to all the clients using one of the methods described above. Next, set `in.txt` to:
```
blank.jpg
500
0 A.png
250
1 B.png
250
2 C.png
500
A.png
500
B.png
500
C.png
500
```
Now we've got something fun.

If you write just an image name without a computer, it tells all of the clients to display that. And remember, writing just a number sleeps for that number. Try and work out what this script will do.

Last is true animation. So far in scripting, this only allows displaying static images, although we can display a sequence of them now. To  do something like moving an image across a monitor, use the `animate` command. Here is the syntax:
```
animate: <filename>, (<x_initial>:<width_initial>, <y_initial>:<height_initial>), (<x_final>:<width_final>, <y_final>:<height_initial>), FILL|CROP|LOCATION, <time>, <clearBuffer [optional]>
```

Going left to write:
1. `filename`: self explanatory
2. `(<x_initial>:<width_initial>, <y_initial>:<height_initial>)`: set the x, y, width, and height values at the start of the animation
3. `(<x_final>:<width_final>, <y_final>:<height_initial>)`: set the x, y, width, and height values at the end of the animation
4. `FILL|CROP|LOCATION`: this defines what the animation actually does. Not case sensitive. `FILL` crops an image at x, y, width, and height, and scales it to fill the screen. `CROP` also crops the image, but does not scale. `LOCATION` draws the complete image at rectangle x, y, width, and height.
5. `time`: animation time in ms
6. `clearBuffer`: draw screen black before each animation frame. `true`/`false`. If this is `false`, it can create some cool effects not otherwise possible. `true` by default.

So, if we have...
```
animate: purple.jpg, (960:1, 540:1), (0:1920, 0:1080), location, 2500, false
```
(Note: This assumes a 1920x1080 `purple.jpg` file)

...then the file `purple.jpg` will be drawn onto the screen as a 1x1 rectangle in the center of the screen (960,540). For 2.5 seconds, it will animate to become a 1920x1080 rectangle filling the whole screen, with a left corner at (0,0).

That's all there is to it. Happy animating.
