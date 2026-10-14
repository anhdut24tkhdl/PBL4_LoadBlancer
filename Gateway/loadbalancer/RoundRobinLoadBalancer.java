package Gateway.loadbalancer;

import Gateway.model.BackendServer;
import Gateway.registry.ServerRegistry;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Thuật toán Round Robin:
 * Phân phối lần lượt các request đến từng server đang hoạt động theo vòng tròn.
 */
public class RoundRobinLoadBalancer implements LoadBalancer {
    private final ServerRegistry registry;
    private final AtomicInteger counter = new AtomicInteger(0);

    public RoundRobinLoadBalancer(ServerRegistry registry) {
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

        // Lấy chỉ số tiếp theo và dùng modulo số lượng server đang alive
        int index = Math.abs(counter.getAndIncrement() % aliveServers.size());
        return aliveServers.get(index);
    }
}
