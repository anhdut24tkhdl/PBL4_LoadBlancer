package Server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class GenericServer {

    private final String name;
    private final int port;
    private final SystemMonitor monitor;

    private final ExecutorService threadPool =
            Executors.newFixedThreadPool(20);

    private volatile boolean running = true;
    private ServerSocket serverSocket;

    public GenericServer(
            String name,
            int port,
            SystemMonitor monitor) {

        this.name = name;
        this.port = port;
        this.monitor = monitor;
    }

    public void start() {

        try {
            serverSocket = new ServerSocket(port);

            System.out.println("=================================================");
            System.out.println(
                    "   " + name + " da khoi dong tai port: " + port);
            System.out.println("=================================================");

            while (running) {
                Socket clientSocket = serverSocket.accept();

                threadPool.execute(
                        new RequestHandler(
                                clientSocket,
                                name,
                                monitor));
            }

        } catch (IOException e) {

            if (running) {
                System.err.println(
                        "[" + name + "] Loi server: "
                                + e.getMessage());
            }

        } finally {
            stop();
        }
    }

    public synchronized void stop() {

        if (!running) {
            return;
        }

        running = false;

        try {
            if (serverSocket != null
                    && !serverSocket.isClosed()) {

                serverSocket.close();
            }
        } catch (IOException e) {
            System.err.println(
                    "[" + name + "] Loi dong server: "
                            + e.getMessage());
        }

        threadPool.shutdown();

        System.out.println(
                "[" + name + "] Da dung.");
    }
}