package Server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class GenericServer {
    private final String name;
    private final int port;
    private final SystemMonitor monitor;
    private final ExecutorService threadPool = Executors.newFixedThreadPool(20);
    private volatile boolean running = true;
    private ServerSocket serverSocket;

    public GenericServer(String name, int port, SystemMonitor monitor) {
        this.name = name;
        this.port = port;
        this.monitor = monitor;
    }

    public void start() {
        // Đăng ký Hook giải phóng tài nguyên khi tắt (Ctrl + C)
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\n[" + name + "] Dang dung server...");
            stop();
        }));

        try {
            serverSocket = new ServerSocket(port);
            System.out.println("=================================================");
            System.out.println("   " + name + " đã khởi động tại port: " + port);
            System.out.println("=================================================");

            while (running) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    threadPool.execute(new RequestHandler(clientSocket, name, monitor));
                } catch (IOException e) {
                    if (!running) break;
                    System.err.println("[" + name + "] Lỗi accept kết nối: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            System.err.println("[" + name + "] Lỗi khởi động ServerSocket: " + e.getMessage());
            e.printStackTrace();
        } finally {
            stop();
        }
    }

    public synchronized void stop() {
        running = false;
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            System.err.println("[" + name + "] Lỗi đóng socket: " + e.getMessage());
        }

        threadPool.shutdown();
        try {
            if (!threadPool.awaitTermination(3, TimeUnit.SECONDS)) {
                threadPool.shutdownNow();
            }
        } catch (InterruptedException e) {
            threadPool.shutdownNow();
        }
        System.out.println("[" + name + "] Đã dừng hoàn toàn.");
    }
}
