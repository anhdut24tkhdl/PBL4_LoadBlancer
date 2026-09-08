package Server.Server2;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class Server2 {
    private static final int DEFAULT_PORT = 5002;
    private final int port;
    private final ExecutorService threadPool = Executors.newFixedThreadPool(20);
    private volatile boolean running = true;
    private ServerSocket serverSocket;

    public Server2() {
        this(DEFAULT_PORT);
    }

    public Server2(int port) {
        this.port = port;
    }

    public void start(SystemMonitor monitor) {
        // Đăng ký Shutdown Hook để đóng tài nguyên an toàn khi tắt tiến trình (Ctrl+C)
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\n[Server 2] Đang dừng server và giải phóng tài nguyên...");
            stop();
        }));

        try {
            serverSocket = new ServerSocket(port);
            System.out.println("=================================================");
            System.out.println("   Backend Server 2 đã khởi động tại port: " + port);
            System.out.println("=================================================");

            while (running) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    threadPool.execute(new RequestHandler(clientSocket, monitor));
                } catch (IOException e) {
                    if (!running) {
                        break; // Server socket đã đóng trong shutdown hook
                    }
                    System.err.println("[Server 2] Lỗi accept kết nối: " + e.getMessage());
                }
            }

        } catch (Exception e) {
            System.err.println("[Server 2] Lỗi khởi động ServerSocket: " + e.getMessage());
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
            System.err.println("[Server 2] Lỗi đóng server socket: " + e.getMessage());
        }

        threadPool.shutdown();
        try {
            if (!threadPool.awaitTermination(3, TimeUnit.SECONDS)) {
                threadPool.shutdownNow();
            }
        } catch (InterruptedException e) {
            threadPool.shutdownNow();
        }
        System.out.println("[Server 2] Đã dừng hoàn toàn.");
    }

    public static void main(String[] args) {
        int port = DEFAULT_PORT;
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.out.println("[Server 2] Port không hợp lệ, sử dụng port mặc định: " + DEFAULT_PORT);
            }
        }

        SystemMonitor monitor = new SystemMonitor();
        Server2 server = new Server2(port);
        server.start(monitor);
    }
}