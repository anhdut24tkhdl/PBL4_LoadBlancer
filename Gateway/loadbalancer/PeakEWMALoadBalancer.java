package Gateway.loadbalancer;

import Gateway.model.BackendServer;
import Gateway.registry.ServerRegistry;

import java.util.concurrent.ThreadLocalRandom;

public class PeakEWMALoadBalancer implements LoadBalancer {

    private final ServerRegistry registry;

    public PeakEWMALoadBalancer(ServerRegistry registry) {
        this.registry = registry;
    }

    @Override
    public synchronized BackendServer selectServer() {
        BackendServer best = null;
        double bestScore = Double.POSITIVE_INFINITY;
        int tiedServers = 0;
        long nowNanos = System.nanoTime();

        for (BackendServer server : registry.getServers()) {
            if (!server.isAlive()) {
                continue;
            }

            int pending = server.getActiveConnections();
            double latencyMs = server.getMetrics().getPeakLatencyMs(nowNanos);
            double score;

            if (latencyMs < 0.0) {
                // Cho backend mới một request thăm dò.
                // Nếu đã có request thăm dò, ưu tiên backend có số đo.
                score = pending == 0 ? 0.0 : Double.MAX_VALUE;
            } else {
                score = latencyMs * (pending + 1.0);
            }

            if (score < bestScore) {
                best = server;
                bestScore = score;
                tiedServers = 1;
            } else if (score == bestScore) {
                tiedServers++;
                if (ThreadLocalRandom.current().nextInt(tiedServers) == 0) {
                    best = server;
                }
            }
        }

        return best;
    }

    @Override
    public synchronized BackendServer selectServerForRequest() {
        BackendServer server = selectServer();

        if (server != null) {
            server.increaseConnections();
        }

        return server;
    }
}