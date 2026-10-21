package Gateway;

import Gateway.api.DashboardApiServer;
import Gateway.forwarding.RequestForwarder;
import Gateway.handler.ClientHandler;
import Gateway.loadbalancer.DynamicLoadBalancer;
import Gateway.loadbalancer.LoadBalancer;
import Gateway.loadbalancer.LeastConnectionLoadBalancer;
import Gateway.loadbalancer.RoundRobinLoadBalancer;
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
    private final String algorithm;

    private final ExecutorService requestPool = Executors.newFixedThreadPool(20);
    private final ScheduledExecutorService monitorPool = Executors.newScheduledThreadPool(3);

    public GatewayServer() {
        this(null);
    }

    public GatewayServer(String algorithmOverride) {
        this.gatewayPort = config.getInt("gateway.port", 5000);
        this.dashboardPort = config.getInt("dashboard.port", 5005);
        if (algorithmOverride != null && !algorithmOverride.trim().isEmpty()) {
            this.algorithm = algorithmOverride.trim();
        } else {
            this.algorithm = config.getString("loadbalancer.algorithm", "round_robin").trim();
        }
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
            System.out.println("   Thuật toán: " + loadBalancer.getClass().getSimpleName());
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

    private LoadBalancer createLoadBalancer(String algo, ServerRegistry registry)
    {
    String key = algo.toLowerCase();
    switch (key) {
    case "least_connection":
    case "leastconnection":
    case "least_conn":
    case "lc":
    System.out.println("[Gateway] Kích hoạt thuật toán: LEAST CONNECTION");
    return new LeastConnectionLoadBalancer(registry);

    case "dynamic":
    System.out.println("[Gateway] Kích hoạt thuật toán: DYNAMIC
    (Multi-metrics)");
    return new DynamicLoadBalancer(registry);

    case "round_robin":
    case "roundrobin":
    case "rr":
    default:
    System.out.println("[Gateway] Kích hoạt thuật toán: ROUND ROBIN");
    return new RoundRobinLoadBalancer(registry);
    }
    }

    public static void main(String[] args) {
        String algoArg = args.length > 0 ? args[0] : null;
        new GatewayServer(algoArg).start();
    }
}