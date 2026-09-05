package Gateway.api;

import Gateway.model.BackendServer;
import Gateway.model.ServerMetrics;
import Gateway.registry.ServerRegistry;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;

public class DashboardApiServer {
    private final ServerRegistry registry;
    private HttpServer httpServer;

    public DashboardApiServer(ServerRegistry registry) {
        this.registry = registry;
    }

    public void start() throws IOException {
        httpServer = HttpServer.create(
                new InetSocketAddress(5005),
                0);

        httpServer.createContext(
                "/api/servers",
                this::handleServers);

        httpServer.setExecutor(
                Executors.newFixedThreadPool(2));

        httpServer.start();

        System.out.println(
                "Dashboard API: http://localhost:5005/api/servers");
    }

    private void handleServers(HttpExchange exchange)
            throws IOException {

        if (!"GET".equals(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(405, -1);
            return;
        }

        StringBuilder json = new StringBuilder("[");

        for (int i = 0; i < registry.getServers().size(); i++) {
            BackendServer server = registry.getServers().get(i);

            ServerMetrics metrics = server.getMetrics();

            double cpu = metrics == null
                    ? 0
                    : metrics.getCpuUsage();

            double ram = metrics == null
                    ? 0
                    : metrics.getRamUsage();

            double latency = metrics == null
                    ? 0
                    : metrics.getLatency();

            json.append("{")
                    .append("\"name\":\"")
                    .append(server.getName())
                    .append("\",")

                    .append("\"host\":\"")
                    .append(server.getHost())
                    .append(":")
                    .append(server.getPort())
                    .append("\",")

                    .append("\"alive\":")
                    .append(server.isAlive())
                    .append(",")

                    .append("\"cpu\":")
                    .append(cpu)
                    .append(",")

                    .append("\"ram\":")
                    .append(ram)
                    .append(",")

                    .append("\"latency\":")
                    .append(latency)
                    .append(",")

                    .append("\"connections\":")
                    .append(server.getActiveConnections())

                    .append("}");

            if (i < registry.getServers().size() - 1) {
                json.append(",");
            }
        }

        json.append("]");

        byte[] response = json.toString()
                .getBytes(StandardCharsets.UTF_8);

        exchange.getResponseHeaders().set(
                "Content-Type",
                "application/json; charset=UTF-8");

        // Cho phép dashboard HTML gọi API, dù mở file trực tiếp.
        exchange.getResponseHeaders().set(
                "Access-Control-Allow-Origin",
                "*");

        exchange.sendResponseHeaders(200, response.length);

        exchange.getResponseBody().write(response);
        exchange.close();
    }
}