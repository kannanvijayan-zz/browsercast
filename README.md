# browsercast
An architecture for writing hybrid apps that run simultaneously on phones and remote endpoints, enabling
rich interaction between them.

# Repository organization

Files are organized as follows:

  'handshake'
  Contains the python scripts that run on the Raspberry Pi (a reasonably recent Raspbian with Avahi and
  Chromium installed should be fine).
  
  'flypic'
  Contain the Java source code for the android app.  The 'app' subdirectory contains the main app, and
  the 'nanohttpd' subdirectory contains the modified NanoHTTPD sources I use within my app.
