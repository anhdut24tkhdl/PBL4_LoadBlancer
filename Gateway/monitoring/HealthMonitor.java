package Gateway.monitoring;

import Gateway.model.BackendServer;
import Gateway.model.ServerMetrics;
import Gateway.registry.ServerRegistry;
import Gateway.utils.AppLogger;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
            boolean wasAlive = server.isAlive();
            boolean alive = checkServer(server);

            server.setAlive(alive);

            if (alive != wasAlive) {
                AppLogger.LOGGER.info("event=BACKEND_STATE_CHANGED"
                        + " backend=" + server.getName()
                        + " state=" + (alive ? "UP" : "DOWN"));
            }

            System.out.println(server.getName() + ": "
                    + (alive ? "UP" : "DOWN")
                    + " Client :" + server.getActiveConnections());
        }
    }

    private boolean checkServer(BackendServer server) {
        long start = System.nanoTime();

        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(
                    server.getHost(), server.getPort()), 1000);
            socket.setSoTimeout(1000);

            OutputStream writer = socket.getOutputStream();
            InputStream reader = socket.getInputStream();

            String request = "GET /metrics HTTP/1.1\r\n"
                    + "Host: " + server.getHost() + "\r\n"
                    + "Connection: close\r\n\r\n";

            writer.write(request.getBytes(StandardCharsets.UTF_8));
            writer.flush();

            String statusLine = readHttpLine(reader);
            if (statusLine == null || !statusLine.contains(" 200 ")) {
                return false;
            }

            int contentLength = -1;

            while (true) {
                String header = readHttpLine(reader);
                if (header == null || header.isEmpty()) break;

                if (header.toLowerCase().startsWith("content-length:")) {
                    contentLength = Integer.parseInt(
                            header.substring(header.indexOf(':') + 1).trim());
                }
            }

            if (contentLength < 0) return false;

            byte[] bodyBytes = reader.readNBytes(contentLength);
            if (bodyBytes.length != contentLength) return false;

            String body = new String(bodyBytes, StandardCharsets.UTF_8);

            Matcher matcher = Pattern
                    .compile("\"cpu\":([0-9.]+),\"ram\":([0-9.]+)")
                    .matcher(body);

            if (!matcher.find()) return false;

            double cpu = Double.parseDouble(matcher.group(1));
            double ram = Double.parseDouble(matcher.group(2));
            double latencyMs = (System.nanoTime() - start) / 1_000_000.0;

            ServerMetrics metrics = new ServerMetrics();

            // Chưa cập nhật metrics vì cần xác nhận API của ServerMetrics
            // và BackendServer.

            return true;

        } catch (Exception e) {
            System.out.println("Không kiểm tra được "
                    + server.getName() + ": " + e.getMessage());
            return false;
        }
    }

    private String readHttpLine(InputStream input) throws Exception {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int current;

        while ((current = input.read()) != -1) {
            if (current == '\n') break;
            if (current != '\r') buffer.write(current);
        }

        if (current == -1 && buffer.size() == 0) return null;

        return buffer.toString(StandardCharsets.UTF_8);
    }
}
