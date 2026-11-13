
package Gateway.loadbalancer;

import Gateway.model.BackendServer;
import Gateway.registry.ServerRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class WeightedLeastConnectionLoadBalancer implements LoadBalancer {

    private final ServerRegistry registry;
    private final AtomicInteger tieBreaker = new AtomicInteger(0);

    private static final double EPSILON = 1e-9;

    public WeightedLeastConnectionLoadBalancer(ServerRegistry registry) {
        this.registry = registry;
    }

    private double calculateScore(BackendServer server) {
        return (double) server.getActiveConnections()
                / server.getWeight();
    }

    private BackendServer findBestServer() {

        List<BackendServer> candidates = new ArrayList<>();

        double minScore = Double.MAX_VALUE;

        for (BackendServer server : registry.getServers()) {

            if (!server.isAlive()) {
                continue;
            }

            double score = calculateScore(server);

            if (score < minScore - EPSILON) {

                minScore = score;
                candidates.clear();
                candidates.add(server);

            } else if (Math.abs(score - minScore) < EPSILON) {

                candidates.add(server);
            }
        }

        if (candidates.isEmpty()) {
            return null;
        }

        // Phá hòa bằng Round Robin
        int index = Math.floorMod(
                tieBreaker.getAndIncrement(),
                candidates.size()
        );

        return candidates.get(index);
    }

    @Override
    public BackendServer selectServer() {
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
