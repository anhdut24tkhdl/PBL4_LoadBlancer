package Server;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

import Gateway.protocol.Request;
import Gateway.protocol.Response;

public class RequestHandler implements Runnable {
    private final Socket clientSocket;
    private final String serverName;
    private final SystemMonitor systemMonitor;

    public RequestHandler(
            Socket clientSocket,
            String serverName,
            SystemMonitor systemMonitor) {
        this.clientSocket = clientSocket;
        this.serverName = serverName;
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

            Request request = Request.parse(reader.readLine());

            if (request == null) {
                return;
            }

            if ("METRICS".equals(request.getCommand())) {
                // Phản hồi cho Gateway HealthMonitor
                SystemMonitor.Metrics metrics = systemMonitor.getCurrentMetrics();
                String metricsResponse = metrics.cpuPercent() + " " + metrics.ramPercent();
                writer.println(metricsResponse);
            } else {
                // Xử lý request từ Gateway / Client
                System.out.println("[" + serverName + "] Nhận request: '" + request + "' từ " + clientAddress);

                // Giả lập thời gian xử lý (3000ms)
                long startTime = System.currentTimeMillis();
                long durationTarget = 3000; // Ép CPU chạy trong 3 giây

                while (System.currentTimeMillis() - startTime < durationTarget) {
                    // Ép CPU tính toán liên tục
                    Math.sqrt(Math.random() * 1000000.0);
                }
                // try {
                //     Thread.sleep(6000);
                    
                    
                // } catch (InterruptedException ie) {
                //     Thread.currentThread().interrupt();
                // }
                long duration = System.currentTimeMillis() - startTime;

                Response res = new Response(200, request.getRequestId(), serverName, "Đã xử lý xong: " + request.getPayload());
                writer.println(res.serialize());

                System.out.println("[" + serverName + "] Hoàn thành sau " + duration + "ms cho: " + clientAddress);
            }

        } catch (Exception e) {
            System.err.println("[" + serverName + "] Lỗi xử lý: " + e.getMessage());
        }
    }
}