package Gateway;

import Gateway.api.DashboardApiServer;
import Gateway.forwarding.RequestForwarder;
import Gateway.handler.ClientHandler;
import Gateway.loadbalancer.DynamicLoadBalancer;
import Gateway.loadbalancer.LoadBalancer;
import Gateway.model.BackendServer;
import Gateway.monitoring.HealthMonitor;
import Gateway.registry.ServerRegistry;

import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public class GatewayServer {
    private final ConfigLoader config = new ConfigLoader();
    private final int gatewayPort;
    private final int dashboardPort;

    private final ExecutorService requestPool = Executors.newFixedThreadPool(20);
    private final ScheduledExecutorService monitorPool = Executors.newScheduledThreadPool(3);

    public GatewayServer() {
        this.gatewayPort = config.getInt("gateway.port", 5000);
        this.dashboardPort = config.getInt("dashboard.port", 5005);
    }

    public void start() {
        ServerRegistry registry = new ServerRegistry();

        // Đọc danh sách server linh hoạt từ file config.properties
        int serverCount = config.getInt("server.count", 3);
        for (int i = 1; i <= serverCount; i++) {
            String name = config.getString("server." + i + ".name", "Server " + i);
            String host = config.getString("server." + i + ".host", "localhost");
            int port = config.getInt("server." + i + ".port", 5000 + i);
            registry.addServer(new BackendServer(name, host, port));
            System.out.println("[Gateway] Đã nạp " + name + " -> " + host + ":" + port);
        }

        LoadBalancer loadBalancer = new DynamicLoadBalancer(registry);
        RequestForwarder forwarder = new RequestForwarder();
        HealthMonitor healthMonitor = new HealthMonitor(registry);
        healthMonitor.start(monitorPool);

        DashboardApiServer dashboardApi = new DashboardApiServer(registry, dashboardPort);
        try {
            dashboardApi.start();
        } catch (Exception e) {
            e.printStackTrace();
        }

        try (ServerSocket serverSocket = new ServerSocket(gatewayPort)) {
            System.out.println("=================================================");
            System.out.println("   Gateway đang chạy tại port " + gatewayPort);
            System.out.println("   Dashboard API tại: http://localhost:" + dashboardPort + "/api/servers");
            System.out.println("=================================================");

            while (true) {
                Socket clientSocket = serverSocket.accept();
                requestPool.execute(new ClientHandler(clientSocket, loadBalancer, forwarder));
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            requestPool.shutdown();
            monitorPool.shutdown();
        }
    }

    public static void main(String[] args) {
        new GatewayServer().start();
    }
}