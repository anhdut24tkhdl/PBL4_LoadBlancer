package Server;

import java.io.IOException;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class GenericServer {
    private final String name;
    private final int port;
    private final SystemMonitor monitor;
    private final ThreadPoolExecutor threadPool = new ThreadPoolExecutor(
            20, 20, 0L, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(100),
            new ThreadPoolExecutor.AbortPolicy());

    private volatile boolean running = true;
    private ServerSocket serverSocket;

    public GenericServer(String name, int port, SystemMonitor monitor) {
        this.name = name;
        this.port = port;
        this.monitor = monitor;
    }

    public void start() {
        try {
            serverSocket = new ServerSocket(port);

            System.out.println("=================================================");
            System.out.println("   " + name + " da khoi dong tai port: " + port);
            System.out.println("=================================================");

            while (running) {
                Socket clientSocket = serverSocket.accept();
                try {
                    threadPool.execute(new RequestHandler(clientSocket, name, monitor));
                } catch (RejectedExecutionException e) {
                    rejectClient(clientSocket);
                    System.err.println("[" + name + "] Qua tải: từ chối request mới vì thread pool và queue đã đầy.");
                }
            }
        } catch (IOException e) {
            if (running) {
                System.err.println("[" + name + "] Loi server: " + e.getMessage());
            }
        } finally {
            stop();
        }
    }

    private void rejectClient(Socket clientSocket) {
        byte[] body = "Service Unavailable".getBytes(StandardCharsets.UTF_8);
        try (Socket socket = clientSocket; OutputStream output = socket.getOutputStream()) {
            String headers = "HTTP/1.1 503 Service Unavailable\r\n"
                    + "Content-Type: text/plain; charset=UTF-8\r\n"
                    + "Content-Length: " + body.length + "\r\n"
                    + "Connection: close\r\n\r\n";
            output.write(headers.getBytes(StandardCharsets.US_ASCII));
            output.write(body);
            output.flush();
        } catch (IOException e) {
            System.err.println("[" + name + "] Không thể gửi phản hồi 503 cho client bị từ chối: " + e.getMessage());
        }
    }

    public synchronized void stop() {
        if (!running) {
            return;
        }

        running = false;

        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            System.err.println("[" + name + "] Loi dong server: " + e.getMessage());
        }

        threadPool.shutdown();
        System.out.println("[" + name + "] Da dung.");
    }
}
