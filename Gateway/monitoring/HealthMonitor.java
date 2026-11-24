
package Gateway.monitoring;

import Gateway.model.BackendServer;
import Gateway.model.ServerMetrics;
import Gateway.registry.ServerRegistry;
import Gateway.utils.AppLogger;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.net.URI;
import java.net.URISyntaxException;

public class HealthMonitor {

    private final ServerRegistry registry;
    private final String metricsPath;

    public HealthMonitor(ServerRegistry registry, String metricsPath) {
        this.registry = registry;
        this.metricsPath = metricsPath;
    }

    public void start(ScheduledExecutorService monitorPool) {
        monitorPool.scheduleAtFixedRate(
                this::checkAllServers,
                0,
                1,
                TimeUnit.SECONDS
        );
    }

    private void checkAllServers() {
        for (BackendServer server : registry.getServers()) {

            boolean wasAlive = server.isAlive();
            boolean alive = checkServer(server);

            server.setAlive(alive);

            if (alive != wasAlive) {
                AppLogger.LOGGER.info(
                        "event=BACKEND_STATE_CHANGED"
                        + " backend=" + server.getName()
                        + " state=" + (alive ? "UP" : "DOWN")
                );
            }

            if (alive) {
                collectMetrics(server);
            } else {
                ServerMetrics metrics = server.getMetrics();
                metrics.setCpuUsage(-1);
                metrics.setRamUsage(-1);
                metrics.setLatency(-1);
                metrics.resetPeakLatency();
            }
      
            System.out.println(
                    server.getName()
                    + ": " + (alive ? "UP" : "DOWN")
                    + " | Connections: " + server.getActiveConnections()
                    + " | CPU: " + server.getMetrics().getCpuUsage()
                    + " | RAM: " + server.getMetrics().getRamUsage()
                    + " | Latency: " + server.getMetrics().getLatency()
            );
        }
    }

    // Kiểm tra TCP: Tomcat có đang lắng nghe hay không?
    private boolean checkServer(BackendServer server) {
        try (Socket socket = new Socket()) {

            socket.connect(
                    new InetSocketAddress(
                            server.getHost(),
                            server.getPort()
                    ),
                    1000
            );

            return true;

        } catch (IOException e) {
            return false;
        }
    }

    // Lấy CPU và RAM từ MetricsServlet trên Tomcat
    private void collectMetrics(BackendServer server) {

        HttpURLConnection connection = null;
        ServerMetrics metrics = server.getMetrics();

        try {
            URI backendBase = new URI(
                    "http", null, server.getHost(), server.getPort(), "/", null, null
            );

            URI metricsEndpoint = backendBase.resolve(metricsPath);
            URL url = metricsEndpoint.toURL();

            connection = (HttpURLConnection) url.openConnection();

            connection.setRequestMethod("GET");
            connection.setConnectTimeout(1000);
            connection.setReadTimeout(1000);
            connection.setRequestProperty("Accept", "text/plain");

            if (connection.getResponseCode() != 200) {
                throw new IOException("Metrics API did not return 200");
            }

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(
                            connection.getInputStream(),
                            StandardCharsets.UTF_8
                    ))) {

                String response = reader.readLine();

                if (response == null) {
                    throw new IOException("Empty metrics response");
                }

                // Ví dụ: "35.20 61.50"
                String[] values = response.trim().split("\\s+");

                if (values.length != 2) {
                    throw new IOException("Invalid metrics format");
                }

                double cpu = Double.parseDouble(values[0]);
                double ram = Double.parseDouble(values[1]);

                if (!Double.isFinite(cpu)
                        || !Double.isFinite(ram)
                        || cpu < 0 || cpu > 100
                        || ram < 0 || ram > 100) {
                    throw new IOException("Invalid CPU/RAM values");
                }

                metrics.setCpuUsage(cpu);
                metrics.setRamUsage(ram);
            }

        } catch (IOException | URISyntaxException | NumberFormatException e) {

            // Không lấy được metrics không có nghĩa Tomcat đã DOWN.
            metrics.setCpuUsage(-1);
            metrics.setRamUsage(-1);

            AppLogger.LOGGER.fine(
                    "event=METRICS_UNAVAILABLE"
                    + " backend=" + server.getName()
                    + " reason=" + e.getMessage()
            );

        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }
}
