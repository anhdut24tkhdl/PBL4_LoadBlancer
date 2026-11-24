
package Gateway.loadbalancer;

import Gateway.model.BackendServer;
import Gateway.model.ServerMetrics;
import Gateway.registry.ServerRegistry;

import java.util.ArrayList;
import java.util.List;

public class DynamicLoadBalancer implements LoadBalancer {

    private final ServerRegistry registry;
    private static final double EPSILON = 1e-9;
    private int tieBreaker = 0;

    public DynamicLoadBalancer(ServerRegistry registry) {
        this.registry = registry;
    }

    private double safePercent(double value) {
        if (!Double.isFinite(value) || value < 0) {
            return 50.0;
        }
        return Math.min(100.0, value);
    }

    private double safeLatency(double value) {
        if (!Double.isFinite(value) || value < 0) {
            return 250.0;
        }
        return value;
    }

    @Override
    public synchronized BackendServer selectServer() {

        double lowestScore = Double.MAX_VALUE;
        List<BackendServer> candidates = new ArrayList<>();

        for (BackendServer server : registry.getServers()) {

            if (!server.isAlive() || server.getMetrics() == null) {
                continue;
            }

            ServerMetrics metrics = server.getMetrics();

            double normCpu = safePercent(
                    metrics.getCpuUsage()
            );

            double normRam = safePercent(
                    metrics.getRamUsage()
            );

            double normLatency = safeLatency(metrics.getLatency()) / 1000.0 * 100.0;
            double normConn = server.getActiveConnections() / 50.0 * 100.0;

            double score =
                    0.35 * normCpu
                    + 0.30 * normRam
                    + 0.20 * normLatency
                    + 0.15 * normConn;

            if (score < lowestScore - EPSILON) {
                lowestScore = score;
                candidates.clear();
                candidates.add(server);

            } else if (Math.abs(score - lowestScore) <= EPSILON) {
                candidates.add(server);
            }
        }

        if (candidates.isEmpty()) {
            return null;
        }

        int index = Math.floorMod(
                tieBreaker++,
                candidates.size()
        );

        return candidates.get(index);
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
