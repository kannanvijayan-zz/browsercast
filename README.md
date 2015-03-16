# browsercast
An architecture for writing hybrid apps that run simultaneously on phones and remote endpoints, enabling
rich interaction between them.

# Repository organization

Files are organized as follows:

* handshake - contains the python scripts that run on the Raspberry Pi.
* flypic - contains the Java source code for the android app.
* flypic/app - The subdirectory containining the main app source code.
* flypic/nanohttpd - Subdirectory containining the modified NanoHTTPD sources I use.

# Installation

## On the Raspberry Pi
Make sure your Raspberry Pi is running Raspbian, and has the following tools installed:

* Avahi
* The python-dbus module.
* The python-avahi module.
* The Chromium web browser.

Put all the files in the 'handshake' directory somewhere on the filesystem.

## On the Android

Build flypic from the sources (I don't have enough experience with Android Studio to
give directions on this matter.. figure it out).

Put the app on your Android phone.  I've only tested it on my Nexus 5 running Lollipop.
It should work on any Android OS which supports the basic features I use (service discovery,
http server, etc.).

# Running

Make sure the Pi is connected to the local network (I used a wired network connection), and that the Android phone is also on that network (wia wifi).

Run 'python handshake.py' to start up the handshake HTTP server on the pi (CTRL-C will kill it).
Run 'python publish.py' to start up the service discovery on the pi (CTRL-C will kill it).

Both 'handshake.py' and 'publish.py' need to be running for things to work.

Start the FlyPic app on the phone, and it should show you the endpoint (by default 'Television').
If you want the endpoint name to be different, change the string in the following line
in publish.py:

    service_thread = ServiceThread('Television', 2015)

Touching the endpoint name in the app will start the browser on the device.  By default,
the handshake server attempts to start Chromium.  If you want to change this, alter the
following line in HandshakeThread.py:

    BROWSER_PATH = '/usr/bin/chromium'
