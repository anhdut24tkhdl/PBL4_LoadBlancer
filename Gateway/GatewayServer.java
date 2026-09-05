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
    private static final int GATEWAY_PORT = 5000;

    private final ExecutorService requestPool = Executors.newFixedThreadPool(20);

    private final ScheduledExecutorService monitorPool = Executors.newScheduledThreadPool(3);

    public void start() {
        ServerRegistry registry = new ServerRegistry();

        registry.addServer(new BackendServer("Server 1", "localhost", 5001));
        registry.addServer(new BackendServer("Server 2", "localhost", 5003));
        registry.addServer(new BackendServer("Server 3", "localhost", 5002));

        LoadBalancer loadBalancer = new DynamicLoadBalancer(registry);

        RequestForwarder forwarder = new RequestForwarder();

        HealthMonitor healthMonitor = new HealthMonitor(registry);

        healthMonitor.start(monitorPool);
        DashboardApiServer dashboardApi = new DashboardApiServer(registry);
        try {
            dashboardApi.start();

        } catch (Exception e) {
            e.printStackTrace();
        }

        try (ServerSocket serverSocket = new ServerSocket(GATEWAY_PORT)) {

            System.out.println(
                    "Gateway đang chạy tại port " + GATEWAY_PORT);

            while (true) {
                Socket clientSocket = serverSocket.accept();

                requestPool.execute(
                        new ClientHandler(
                                clientSocket,
                                loadBalancer,
                                forwarder));
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