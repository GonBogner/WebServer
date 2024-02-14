import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class WebServer {
    public static void main(String[] args) {
        ExecutorService executor = null;
        ServerSocket serverSocket = null;
        try {
            ConfigLoader configLoader = new ConfigLoader("config.ini");
            int port = Integer.parseInt(configLoader.getProperty("port"));
            int maxThreads = Integer.parseInt(configLoader.getProperty("maxThreads"));

            executor = Executors.newFixedThreadPool(maxThreads);
            serverSocket = new ServerSocket(port);
            System.out.println("Server is listening on port " + port);

            while (true) {
                Socket socket = serverSocket.accept();
                System.out.println("New client connected");
                executor.execute(new ClientHandler(socket, configLoader));
            }
        } catch (IOException e) {
            System.out.println("Server exception: " + e.getMessage());
            e.printStackTrace();
        } finally {
            if (executor != null) {
                executor.shutdownNow();
            }
            if (serverSocket != null) {
                try {
                    serverSocket.close();
                } catch (IOException e) {
                    System.out.println("Could not close server socket: " + e.getMessage());
                }
            }
        }
    }
}
