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

public class HealthMonitor {
    private final ServerRegistry registry;

    public HealthMonitor(ServerRegistry registry) {
        this.registry = registry;
    }

    public void start(ScheduledExecutorService monitorPool) {
        monitorPool.scheduleAtFixedRate(
                this::checkAllServers,
                0,
                1,
                TimeUnit.SECONDS);
    }

    private void checkAllServers() {
        for (BackendServer server : registry.getServers()) {
            boolean alive = checkServer(server);

            server.setAlive(alive);

            System.out.println(
                    server.getName() + ": "
                            + (alive ? "UP" : "DOWN") + "  Client  :" + server.getActiveConnections());

        }
    }

    private boolean checkServer(BackendServer server) {
        long start = System.nanoTime();

        try (Socket socket = new Socket()) {
            socket.connect(
                    new InetSocketAddress(
                            server.getHost(),
                            server.getPort()),
                    1000);

            socket.setSoTimeout(1000);

            PrintWriter writer = new PrintWriter(
                    socket.getOutputStream(), true);

            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(socket.getInputStream()));

            // println để Backend dùng readLine() có thể nhận lệnh
            writer.println("METRICS");

            // Chỉ đọc đúng một dòng response
            String message = reader.readLine();

            if (message == null) {
                return false;
            }

            String[] parts = message.trim().split("\\s+");

            if (parts.length < 2) {
                return false;
            }

            double cpu = Double.parseDouble(parts[0]);
            double ram = Double.parseDouble(parts[1]);

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