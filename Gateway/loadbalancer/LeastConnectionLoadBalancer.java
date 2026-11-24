
package Gateway.loadbalancer;

import Gateway.model.BackendServer;
import Gateway.registry.ServerRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class LeastConnectionLoadBalancer implements LoadBalancer {

    private final ServerRegistry registry;
    private final AtomicInteger tieBreaker = new AtomicInteger(0);

    public LeastConnectionLoadBalancer(ServerRegistry registry) {
        this.registry = registry;
    }

    private BackendServer findLeastConnectedServer() {

        List<BackendServer> candidates = new ArrayList<>();

        int minConnections = Integer.MAX_VALUE;

        for (BackendServer server : registry.getServers()) {

            if (!server.isAlive()) {
                continue;
            }

            int connections = server.getActiveConnections();

            if (connections < minConnections) {
                minConnections = connections;
                candidates.clear();
                candidates.add(server);
            } else if (connections == minConnections) {
                candidates.add(server);
            }
        }

        if (candidates.isEmpty()) {
            return null;
        }

        // Round Robin khi các server bằng số kết nối
        int index = Math.floorMod(
                tieBreaker.getAndIncrement(),
                candidates.size()
        );

        return candidates.get(index);
    }

    @Override
    public BackendServer selectServer() {
        return findLeastConnectedServer();
    }

    @Override
    public synchronized BackendServer selectServerForRequest() {

        BackendServer server = findLeastConnectedServer();

        if (server != null) {
            server.increaseConnections();
        }

        return server;
    }
}
