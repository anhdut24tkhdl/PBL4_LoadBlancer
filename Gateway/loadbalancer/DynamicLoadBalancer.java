package Gateway.loadbalancer;

import Gateway.model.BackendServer;
import Gateway.model.ServerMetrics;
import Gateway.registry.ServerRegistry;

public class DynamicLoadBalancer implements LoadBalancer {
    private final ServerRegistry registry;

    public DynamicLoadBalancer(ServerRegistry registry) {
        this.registry = registry;
    }

    @Override
    public BackendServer selectServer() {
        BackendServer selectedServer = null;
        double lowestScore = Double.MAX_VALUE;

        for (BackendServer server : registry.getServers()) {

            if (!server.isAlive()) {

                continue;
            }

            ServerMetrics metrics = server.getMetrics();

            double score = 0.35 * metrics.getCpuUsage()
                    + 0.30 * metrics.getRamUsage()
                    + 0.20 * metrics.getLatency()
                    + 0.15 * server.getActiveConnections();

            if (score < lowestScore) {
                lowestScore = score;
                selectedServer = server;

            }
        }

        return selectedServer;
    }
}