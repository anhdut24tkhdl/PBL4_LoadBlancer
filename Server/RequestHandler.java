package Server;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class RequestHandler implements Runnable {
    private final Socket clientSocket;
    private final String name;
    private final SystemMonitor systemMonitor;

    public RequestHandler(
            Socket clientSocket, String name,
            SystemMonitor systemMonitor) {
        this.clientSocket = clientSocket;
        this.name = name;
        this.systemMonitor = systemMonitor;
    }

    @Override
    public void run() {
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

            if ("METRICS".equals(request)) {
                SystemMonitor.Metrics metrics = systemMonitor.getCurrentMetrics();
                writer.println(
                        metrics.cpuPercent() + " "
                                + metrics.ramPercent());
            } else {
                // Mô phỏng thời gian xử lý request lâu hơn (mặc định 3000ms) để quan sát active connections trên Dashboard FE
                try {
                    int delayMs = 3000;
                    if (request.contains("sleep=") || request.contains("delay=")) {
                        if (request.contains("sleep=")) {
                            String[] parts = request.split("sleep=");
                            if (parts.length > 1) {
                                String num = parts[1].split("[ &]")[0];
                                delayMs = Integer.parseInt(num);
                            }
                        } else if (request.contains("delay=")) {
                            String[] parts = request.split("delay=");
                            if (parts.length > 1) {
                                String num = parts[1].split("[ &]")[0];
                                delayMs = Integer.parseInt(num);
                            }
                        }
                    }
                    Thread.sleep(10000);
                } catch (Exception ignored) {}

                Path file = Path.of("Server/banthan.htm");
                if (!Files.exists(file)) {
                    file = Path.of("banthan.htm");
                }

                byte[] bodyBytes;
                String contentType;
                if (Files.exists(file)) {
                    bodyBytes = Files.readAllBytes(file);
                    contentType = "text/html; charset=UTF-8";
                } else {
                    String msg = "<html><body><h1>Response from " + name + "</h1><p>Processed successfully!</p></body></html>";
                    bodyBytes = msg.getBytes(StandardCharsets.UTF_8);
                    contentType = "text/html; charset=UTF-8";
                }

                String header = "HTTP/1.1 200 OK\r\n" +
                        "Content-Type: " + contentType + "\r\n" +
                        "X-Backend-Server: " + name + "\r\n" +
                        "Content-Length: " + bodyBytes.length + "\r\n" +
                        "Connection: close\r\n" +
                        "\r\n";

                socket.getOutputStream().write(header.getBytes(StandardCharsets.UTF_8));
                socket.getOutputStream().write(bodyBytes);
                socket.getOutputStream().flush();
            }

        } catch (Exception e) {
            System.err.println("[" + name + "] Lỗi xử lý request: " + e.getMessage());
        }
    }
}