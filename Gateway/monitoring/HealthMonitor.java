package Gateway.monitoring;

import Gateway.model.BackendServer;
import Gateway.model.ServerMetrics;
import Gateway.registry.ServerRegistry;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class HealthMonitor {

    private final ServerRegistry registry;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(1))
            .build();

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
                            + (alive ? "UP" : "DOWN")
                            + " Client: " + server.getActiveConnections());
        }
    }

    private boolean checkServer(BackendServer server) {

        long start = System.nanoTime();

        try {
            String url = "http://"
                    + server.getHost()
                    + ":"
                    + server.getPort()
                    + "/OnlineExam/api/metrics?cmd=METRICS";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(1))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                System.out.println(
                        server.getName()
                                + " HTTP status: "
                                + response.statusCode());
                return false;
            }

            String message = response.body().trim();

            String[] parts = message.split("\\s+");

            if (parts.length != 2) {
                return false;
            }

            double cpu = Double.parseDouble(parts[0]);
            double ram = Double.parseDouble(parts[1]);

            if (!Double.isFinite(cpu) || !Double.isFinite(ram)
                    || cpu < 0 || cpu > 100
                    || ram < 0 || ram > 100) {
                return false;
            }

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
                            + server.getName()
                            + ": "
                            + e.getMessage());
            return false;
        }
    }
}