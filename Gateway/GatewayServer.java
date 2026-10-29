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

import java.io.IOException;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.logging.Level;
import Gateway.utils.AppLogger;


public class GatewayServer {
    private final ConfigLoader config = new ConfigLoader();
    private final int gatewayPort;
    private final int dashboardPort;
    private final String algorithm;

    private final ThreadPoolExecutor requestPool = new ThreadPoolExecutor(
            10, 10, 0L, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(10),
            new ThreadPoolExecutor.AbortPolicy());
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
            AppLogger.LOGGER.info("event=BACKEND_CONFIGURED backend=" + name + " host=" + host + " port=" + port);
            System.out.println("[Gateway] Đã nạp " + name + " -> " + host + ":" + port);
        }

        LoadBalancer loadBalancer = createLoadBalancer(algorithm, registry);
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
            AppLogger.LOGGER.info("event=GATEWAY_STARTED gateway_port=" + gatewayPort + " dashboard_port=" + dashboardPort
                    + " algorithm=" + loadBalancer.getClass().getSimpleName());
            AppLogger.LOGGER.info("event=GATEWAY_STARTED" + " gateway_port=" + gatewayPort + " dashboard_port=" + dashboardPort);
            System.out.println("=================================================");
            System.out.println("   Gateway đang chạy tại port " + gatewayPort);
            System.out.println("   Thuật toán: " + loadBalancer.getClass().getSimpleName());
            System.out.println("   Dashboard API tại: http://localhost:" + dashboardPort + "/api/servers");
            System.out.println("=================================================");

            while (true) {
                Socket clientSocket = serverSocket.accept();
                try {
                    requestPool.execute(new ClientHandler(clientSocket, loadBalancer, forwarder));
                } catch (RejectedExecutionException e) {
                    rejectClient(clientSocket);
                    AppLogger.LOGGER.warning(
                        "event=GATEWAY_OVERLOADED"
                        + " reason=THREAD_POOL_FULL"
                        + " active_workers=" + requestPool.getActiveCount()
                        + " queue_size=" + requestPool.getQueue().size()
                        + " queue_capacity=" + (requestPool.getQueue().size()
                        + requestPool.getQueue().remainingCapacity())
                    );
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            // Ghi log lỗi nghiêm trọng khi gateway gặp sự cố
            AppLogger.LOGGER.log(Level.SEVERE, "event=GATEWAY_ERROR", e);
        } finally {
            requestPool.shutdown();
            monitorPool.shutdown();
        }
    }

    private void rejectClient(Socket clientSocket) {
        byte[] body = "Service Unavailable".getBytes(StandardCharsets.UTF_8);
        try (Socket socket = clientSocket; OutputStream output = socket.getOutputStream()) {
            String headers = "HTTP/1.1 503 Service Unavailable\r\n"
                    + "Content-Type: text/plain; charset=UTF-8\r\n"
                    + "Content-Length: " + body.length + "\r\n"
                    + "Connection: close\r\n\r\n";
            output.write(headers.getBytes(StandardCharsets.US_ASCII));
            output.write(body);
            output.flush();
        } catch (IOException e) {
            //"Không thể gửi phản hồi 503 cho client bị từ chối."
            AppLogger.LOGGER.log(Level.FINE, "event=REJECTED_CLIENT_503_WRITE_FAILED", e);
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
            System.out.println("[Gateway] Kích hoạt thuật toán: DYNAMIC(Multi-metrics)");
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