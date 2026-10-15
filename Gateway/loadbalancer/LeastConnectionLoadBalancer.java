package Gateway.loadbalancer;

import Gateway.model.BackendServer;
import Gateway.registry.ServerRegistry;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Thuật toán Least Connection:
 * Chuyển request đến server đang có ít kết nối hoạt động nhất (active connections thấp nhất).
 */
public class LeastConnectionLoadBalancer implements LoadBalancer {
    private final ServerRegistry registry;

    public LeastConnectionLoadBalancer(ServerRegistry registry) {
        this.registry = registry;
    }

    @Override
    public BackendServer selectServer() {
        List<BackendServer> aliveServers = registry.getServers().stream()
                .filter(BackendServer::isAlive)
                .collect(Collectors.toList());

        if (aliveServers.isEmpty()) {
            return null;
        }

        // Tìm server có active connections nhỏ nhất
        BackendServer bestServer = aliveServers.get(0);
        int minConnections = bestServer.getActiveConnections();

        for (int i = 1; i < aliveServers.size(); i++) {
            BackendServer current = aliveServers.get(i);
            int currentConn = current.getActiveConnections();
            if (currentConn < minConnections) {
                minConnections = currentConn;
                bestServer = current;
            }
        }

        return bestServer;
    }
}
