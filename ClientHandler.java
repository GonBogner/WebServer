import java.io.*;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.file.*;
import java.util.HashMap;
import java.util.Map;

public class ClientHandler implements Runnable {
    private final Socket clientSocket;
    private final ConfigLoader configLoader;
    private boolean isChunked;
    private final Map<String, String> mimeTypes;
    
    public ClientHandler(Socket socket, ConfigLoader configLoader) {
        this.clientSocket = socket;
        this.configLoader = configLoader;
        this.isChunked = false;

        mimeTypes = new HashMap<>();
        mimeTypes.put("jpg", "image");
        mimeTypes.put("jpeg", "image");
        mimeTypes.put("gif", "image");
        mimeTypes.put("bmp", "image");
        mimeTypes.put("txt", "text/html"); 
        mimeTypes.put("html", "text/html");
        mimeTypes.put("ico", "icon");
    }

    @Override
    public void run() {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true)) {
            StringBuilder requestBuilder = new StringBuilder();
            String line;

            while ((line = in.readLine()) != null && !line.isEmpty()) {
                requestBuilder.append(line).append("\r\n");
            }

            String request = requestBuilder.toString();
            String[] requestLines = request.split("\r\n");
            String[] requestLine = requestLines[0].split(" ");
            String httpMethod = requestLine[0];
            String httpPath = "";
            if (requestLine.length > 1) {
                httpPath = requestLine[1];
            }

    
            System.out.println("run httpPath" + httpPath);
            logRequest(httpMethod, httpPath, requestLines);
 
            isChunked = shouldUseChunked(request);
            // Normalize and resolve the requested path against the root directory
            String rootPath = configLoader.getProperty("root");
            if (rootPath.startsWith("~")) {
                String homeDirectory = System.getProperty("user.home");
                rootPath = rootPath.replaceFirst("~", homeDirectory);
            }
            Path resolvedPath = sanitizePath(rootPath, httpPath);
            Path rootPathObj = Paths.get(rootPath);

            // Check if the resolved path starts with the root directory path
            if (!resolvedPath.startsWith(rootPathObj)) {
                out.println("HTTP/1.1 400 Bad Request\r\n");
                out.println("Content-Type: text/plain");
                out.println();
                out.println("Bad Request: The requested path is not allowed.");
                out.flush();
                return; // Stop further processing
            }
            

            String resolvedFile;
            if (httpPath.equals("/") || resolvedPath.equals(rootPathObj)) {
                resolvedFile = configLoader.getProperty("defaultPage");
            } else {
                resolvedFile = rootPathObj.relativize(resolvedPath).toString();
            }
            
            
            Path file = Paths.get(rootPath, resolvedFile).normalize();
            System.out.println("root: " + rootPath);
            System.out.println("file: " + file);
 
                       
            switch (httpMethod) {
                case "GET":
                    handleGetRequest(out, file);
                    break;
                case "POST":
                    handlePostRequest(in, out, request);
                    break;
                case "HEAD":
                    handleHeadRequest(out, file);
                    break;
                case "TRACE":
                    handleTraceRequest(out, request);
                    break;
                default:
                    out.println("HTTP/1.1 501 Not Implemented\r\n");
                    break;
            }

        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                clientSocket.close();
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
    }

    public static Path sanitizePath(String rootDirectory, String httpPath) {
        Path rootPath = Paths.get(rootDirectory).toAbsolutePath().normalize();
        // Resolve the httpPath against the rootPath and normalize the result
        Path resolvedPath = rootPath.resolve(httpPath.startsWith("/") ? httpPath.substring(1) : httpPath).normalize();
        System.out.println("Normalized path: " + resolvedPath);
        return resolvedPath;
    }
    
    private void logRequest(String httpMethod, String httpPath, String[] requestLines) {
        System.out.println("Received HTTP Request:");
        System.out.println("'" + httpMethod + "'" + " " + "'" + httpPath + "'"); // Prints the request line
        System.out.println("HTTP Request Headers:");
        for (int i = 1; i < requestLines.length; i++) {
            System.out.println("'" + requestLines[i] + "'"); // This prints each header line
        }
    }
    
    private boolean shouldUseChunked(String request) {
        return request.lines()
                      .filter(line -> line.startsWith("chunked:"))
                      .map(line -> line.split(":")[1].trim())
                      .anyMatch("yes"::equalsIgnoreCase);
    }

    private void handleGetRequest(PrintWriter out, Path file) {
        try {
    
            if (Files.exists(file)) {
                byte[] fileBytes = Files.readAllBytes(file);
                String contentType = determineContentType(file);
                System.out.println(isChunked + " isChunked");
                out.println("HTTP/1.1 200 OK");
                out.println("Content-Type: " + contentType);
                if (isChunked) {
                    // Send headers for chunked transfer
                    out.println("Transfer-Encoding: chunked");
                    out.println();
                    out.flush();
    
                    // Send file in chunks
                    sendFileInChunks(file, clientSocket.getOutputStream());
                } 

                else {
                    // Send file normally
                    out.println("Content-Length: " + fileBytes.length);
                    out.println("Connection: close");
                    out.println();
                    out.flush();
                    clientSocket.getOutputStream().write(fileBytes, 0, fileBytes.length);
                    clientSocket.getOutputStream().flush();
                }
            } else {
                out.println("HTTP/1.1 404 Not Found\r\n");
            }
        } catch (IOException e) {
            out.println("HTTP/1.1 500 Internal Server Error\r\n");
        }
    }
    
    private void sendFileInChunks(Path file, OutputStream outputStream) throws IOException {
        byte[] buffer = new byte[512]; // Chunk size - adjust if needed
        try (InputStream fis = Files.newInputStream(file)) {
            int read;
            while ((read = fis.read(buffer)) != -1) {
                // Convert chunk size to hex and write it
                String sizeHeader = Integer.toHexString(read) + "\r\n";
                outputStream.write(sizeHeader.getBytes());
                // Write the chunk
                outputStream.write(buffer, 0, read);
                outputStream.write("\r\n".getBytes());
            }
            // Write the final chunk size (0) to indicate the end
            outputStream.write("0\r\n\r\n".getBytes());
            System.out.println("chunk: final");
        }
    }
    
    
    private String determineContentType(Path file) throws IOException {
        // Get the file extension
        String fileName = file.toString().toLowerCase();
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex > 0) {
            String extension = fileName.substring(dotIndex + 1);
            // Check the MIME type in the custom map first
            String mimeType = mimeTypes.get(extension);
            if (mimeType != null) {
                return mimeType;
            }
        }
    
        // Fallback to automatically determined MIME type
        String contentType = Files.probeContentType(file);
        if (contentType != null) {
            return contentType;
        } else {
            return "application/octet-stream";
        }
    }
    
    

    private void handlePostRequest(BufferedReader in, PrintWriter out, String request) throws IOException {
        String requestLine = request.split("\r\n")[0];
        String target = requestLine.split(" ")[1];
        System.out.println("target: " + target);
        if ("/params_info.html".equals(target)) {
            // Process form submission and generate response
            int contentLength = extractContentLength(request);
            char[] buffer = new char[contentLength];
            in.read(buffer, 0, contentLength);
            String postData = new String(buffer);
            Map<String, String> postParameters = parsePostData(postData);

            System.out.println("HTTP/1.1 200 OK\r\n");
            out.write("HTTP/1.1 200 OK\r\n");
            out.write("Content-Type: text/html\r\n");
            out.write("\r\n");
    
            out.write("<!DOCTYPE html><html><head><title>Form Submission</title></head><body>");
            out.write("<h1>Form Submission Details</h1>");
    
            // Display the submitted message
            if (postParameters.containsKey("message")) {
                out.write("<p><b>Message:</b> " + postParameters.get("message") + "</p>");
            } else {
                out.write("<p><b>Message:</b> (No message provided)</p>");
            }
    
            // Check the status of the checkbox
            if (postParameters.containsKey("loveCN") && postParameters.get("loveCN").equals("yes")) {
                out.write("<p><b>I love Computer Networks:</b> Yes</p>");
            } else {
                out.write("<p><b>I love Computer Networks:</b> No</p>");
            }
    
            out.write("</body></html>");
        } else {
            out.write("HTTP/1.1 404 Not Found\r\n\r\n");
        }
        out.flush();
    }
    
    
    private void handleHeadRequest(PrintWriter out, Path file) {
        try {
            if (Files.exists(file)) {
                byte[] fileBytes = Files.readAllBytes(file);
                String contentType = Files.probeContentType(file);
                out.println("HTTP/1.1 200 OK");
                out.println("Content-Type: " + contentType);
                out.println("Content-Length: " + fileBytes.length);
                out.println("Connection: close");
                out.println();
                out.flush();
            } else {
                out.println("HTTP/1.1 404 Not Found\r\n");
            }
        } catch (IOException e) {
            out.println("HTTP/1.1 500 Internal Server Error\r\n");
        }
    }

    private void handleTraceRequest(PrintWriter out, String request) {
        out.println("HTTP/1.1 200 OK");
        out.println("Content-Type: message/http");
        out.println("Content-Length: " + request.length());
        out.println("Connection: close");
        out.println();
        out.println(request);
        out.flush();
    }

    // Get the content length from the HTTP request
    private int extractContentLength(String request) {
        String[] lines = request.split("\r\n");
        for (String line : lines) {
            if (line.startsWith("Content-Length:")) {
                String[] parts = line.split(":");
                if (parts.length > 1) { // Ensure there is a part after the colon
                    try {
                        return Integer.parseInt(parts[1].trim());
                    } catch (NumberFormatException e) {
                        // Log error or handle it as appropriate
                        System.err.println("Error parsing Content-Length header: " + e.getMessage());
                        return 0; // Returning 0 or consider throwing an exception
                    }
                }
            }
        }
        return 0; // No Content-Length header found, or header is malformed
    }
        

    private Map<String, String> parsePostData(String data) {
        Map<String, String> postParameters = new HashMap<>();
        String[] pairs = data.split("&");
        for (String pair : pairs) {
            int idx = pair.indexOf("=");
            // Only process the pair if it contains an equal sign and splits into two parts
            if (idx > -1 && idx < pair.length() - 1) { // Ensure there is a key and value
                String key = pair.substring(0, idx);
                String value = pair.substring(idx + 1);
                try {
                    postParameters.put(URLDecoder.decode(key, "UTF-8"), URLDecoder.decode(value, "UTF-8"));
                } catch (UnsupportedEncodingException e) {
                    // Since UTF-8 is guaranteed to be supported, this exception should never occur
                    System.err.println("UTF-8 is not supported");
                }
            }
        }
        return postParameters;
    }
}