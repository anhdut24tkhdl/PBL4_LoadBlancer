package com.mycompany.server1;

import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Server1 {
    private static final int PORT = 5001;

    private final ExecutorService threadPool = Executors.newFixedThreadPool(20);

    public void connection(SystemMonitor monitor) {
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Backend chạy tại port " + PORT);

            while (true) {
                Socket clientSocket = serverSocket.accept();

                threadPool.execute(
                        new RequestHandler(clientSocket, monitor));
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        SystemMonitor monitor = new SystemMonitor();
        Server1 server = new Server1();

        server.connection(monitor);
    }
}