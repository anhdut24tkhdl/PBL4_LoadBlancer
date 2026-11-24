
package Gateway.loadbalancer;

import Gateway.model.BackendServer;
import Gateway.registry.ServerRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class WeightedLeastConnectionLoadBalancer implements LoadBalancer {

    private final ServerRegistry registry;
    private long tieBreaker;

    public WeightedLeastConnectionLoadBalancer(ServerRegistry registry) {
        this.registry = registry;
    }

    private BackendServer findBestServer() {
        List<BackendServer> candidates = new ArrayList<>();
        int bestConnections = 0;
        int bestWeight = 1;

        for (BackendServer server : registry.getServers()) {
            if (!server.isAlive()) {
                continue;
            }

            int connections = server.getActiveConnections();
            int weight = server.getWeight();

            // So sánh connections / weight với bestConnections / bestWeight.
            long left = (long) connections * bestWeight;
            long right = (long) bestConnections * weight;

            if (candidates.isEmpty() || left < right) {
                candidates.clear();
                candidates.add(server);
                bestConnections = connections;
                bestWeight = weight;
            } else if (left == right) {
                candidates.add(server);
            }
        }

        if (candidates.isEmpty()) {
            return null;
        }
        if (candidates.size() == 1) {
            return candidates.get(0);
        }

        long totalWeight = 0;
        for (BackendServer server : candidates) {
            totalWeight += server.getWeight();
        }

        long ticket = Math.floorMod(tieBreaker++, totalWeight);
        for (BackendServer server : candidates) {
            if (ticket < server.getWeight()) {
                return server;
            }
            ticket -= server.getWeight();
        }

        throw new IllegalStateException("Weighted selection failed");
    }

    @Override
    public synchronized BackendServer selectServer() {
        return findBestServer();
    }

    @Override
    public synchronized BackendServer selectServerForRequest() {

        BackendServer server = findBestServer();

        if (server != null) {
            server.increaseConnections();
        }

        return server;
    }
}
