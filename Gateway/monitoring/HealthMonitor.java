package Gateway.monitoring;

import Gateway.model.BackendServer;
import Gateway.model.ServerMetrics;
import Gateway.registry.ServerRegistry;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import Gateway.utils.AppLogger;

public class HealthMonitor {
    private final ServerRegistry registry;

    public HealthMonitor(ServerRegistry registry) {
        this.registry = registry;
    }

    public void start(ScheduledExecutorService monitorPool) {
        monitorPool.scheduleAtFixedRate(this::checkAllServers, 0, 1, TimeUnit.SECONDS);
    }

    private void checkAllServers() {
        for (BackendServer server : registry.getServers()) {
            boolean alive = checkServer(server);

            server.setAlive(alive);

            boolean wasAlive = server.isAlive();


            server.setAlive(alive);

            if (wasAlive != alive) {
                // Ghi log thông tin khi trạng thái của backend thay đổi
                AppLogger.LOGGER.info("event=BACKEND_STATE_CHANGED"
                    + " backend=" + server.getName()
                    + " state=" + (alive ? "UP" : "DOWN"));
            }

            System.out.println(
                    server.getName() + ": "
                            + (alive ? "UP" : "DOWN") + " Client :" + server.getActiveConnections());

        }
    }

    private boolean checkServer(BackendServer server) {
        long start = System.nanoTime();

        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress( server.getHost(), server.getPort()), 1000);
            socket.setSoTimeout(1000);

            OutputStream writer = socket.getOutputStream();
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));

            String request = "GET /metrics HTTP/1.1\r\n"
                    + "Host: " + server.getHost() + "\r\n"
                    + "Connection: close\r\n\r\n";

            writer.write(request.getBytes(StandardCharsets.UTF_8));
            writer.flush();

            // Đọc HTTP status line
            String statusLine = reader.readLine();
            if (statusLine == null || !statusLine.contains(" 200 ")) {
                return false;
            }

            // Đọc headers đến dòng trống
            while (true) {
                String header = reader.readLine();
                if (header == null || header.isEmpty()) {
                    break;
                }
            }

            // Đọc JSON body
            StringBuilder body = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                body.append(line);
            }

            String message = body.toString();

            java.util.regex.Matcher matcher = java.util.regex.Pattern
            .compile("\"cpu\":([0-9.]+),\"ram\":([0-9.]+)")
            .matcher(message);

            if (!matcher.find()) {
                return false;
            }

            double cpu = Double.parseDouble(matcher.group(1));
            double ram = Double.parseDouble(matcher.group(2));
            double latencyMs = (System.nanoTime() - start) / 1_000_000.0;

            ServerMetrics metrics = new ServerMetrics();

            metrics.setCpuUsage(cpu);
            metrics.setRamUsage(ram);
            metrics.setLatency(latencyMs);

            server.setMetrics(metrics);

            return true;

        } catch (Exception e) {
            System.out.println(
                    "Không kiểm tra được "
                            + server.getName() + ": "
                            + e.getMessage());

            return false;
        }
    }
}