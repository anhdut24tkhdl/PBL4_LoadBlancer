package Server.Server3;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class RequestHandler implements Runnable {
    private final Socket clientSocket;
    private final SystemMonitor systemMonitor;

    public RequestHandler(
            Socket clientSocket,
            SystemMonitor systemMonitor) {
        this.clientSocket = clientSocket;
        this.systemMonitor = systemMonitor;
    }

    @Override
    public void run() {
        String clientAddress = clientSocket.getRemoteSocketAddress() != null 
                ? clientSocket.getRemoteSocketAddress().toString() 
                : "Unknown";

        try (
                Socket socket = clientSocket;
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(socket.getInputStream()));
                PrintWriter writer = new PrintWriter(
                        socket.getOutputStream(), true)) {

            String request = reader.readLine();

            if (request == null) {
                return;
            }

            if ("METRICS".equalsIgnoreCase(request.trim())) {
                // Phản hồi cho Gateway HealthMonitor
                SystemMonitor.Metrics metrics = systemMonitor.getCurrentMetrics();
                String metricsResponse = metrics.cpuPercent() + " " + metrics.ramPercent();
                writer.println(metricsResponse);
            } else {
                // Xử lý request từ Gateway / Client
                System.out.println("[Server 3] Nhận request: '" + request + "' từ " + clientAddress);

                // Giả lập thời gian tính toán tải (mặc định 250ms)
                long startTime = System.currentTimeMillis();
                try {
                    Thread.sleep(250);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
                long duration = System.currentTimeMillis() - startTime;

                String response = "Server 3 (Port 5003): Đã xử lý thành công request [" + request + "] (" + duration + "ms)";
                writer.println(response);

                System.out.println("[Server 3] Hoàn thành phản hồi sau " + duration + "ms cho: " + clientAddress);
            }

        } catch (Exception e) {
            System.err.println("[Server 3] Lỗi khi xử lý request từ " + clientAddress + ": " + e.getMessage());
        }
    }
}
