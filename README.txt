Web Server Implementation

Authors: Gon Bogner & Uri Meir



Project Overview:
This project implements a simple multi-threaded web server in Java. 
The server is capable of handling GET, POST, HEAD, and TRACE HTTP requests. 
It serves static content such as HTML, images, and other files from a specified root directory. 
Additionally, it supports form submissions via POST requests and returns appropriate responses. 
The server configuration is read from a config.ini file, allowing customization of parameters such as the port number,
root directory, default page, and maximum number of threads.


Class Descriptions:
1.WebServer.java:
- Main class responsible for starting the web server.
- Initializes a thread pool based on the configured maximum number of threads.
- Listens for incoming client connections on the specified port.
- Accepts client connections and delegates handling to ClientHandler instances.

2.ClientHandler.java:
-Handles communication with individual client connections.
-Implements the Runnable interface for concurrent execution.
-Parses HTTP requests, extracts request method, path, and headers.
-Routes requests to appropriate handlers based on the request method.
-Handles GET, POST, HEAD, and TRACE requests by serving files or generating responses.
-Utilizes ConfigLoader to access server configuration parameters.

3.ConfigLoader.java:
-Loads server configuration from the config.ini file.
-Reads port number, root directory, default page, and maximum threads from the configuration file.
-Provides methods to retrieve individual configuration properties.



Design Overview:
The server follows a multi-threaded design to handle concurrent client connections efficiently.
Upon receiving a client connection, a new ClientHandler instance is created to handle communication with the client.
This design allows the server to serve multiple clients simultaneously without blocking on I/O operations.
The server configuration is externalized to a config.ini file, making it easy to customize server parameters without modifying the source code.
Overall, the server is designed to be flexible, scalable, and configurable to meet various requirements for serving static content and handling HTTP requests.