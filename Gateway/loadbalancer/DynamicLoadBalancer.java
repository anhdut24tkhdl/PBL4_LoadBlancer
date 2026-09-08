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

            // Giả định Latency chuẩn tối đa là 1000ms, Connections chuẩn tối đa là 50
            double normCpu = metrics.getCpuUsage();                     // [0 - 100]
            double normRam = metrics.getRamUsage();                     // [0 - 100]
            double normLatency = Math.min(100.0, (metrics.getLatency() / 1000.0) * 100.0);
            double normConn = Math.min(100.0, (server.getActiveConnections() / 50.0) * 100.0);

            double score = 0.35 * normCpu 
                        + 0.30 * normRam 
                        + 0.20 * normLatency 
                        + 0.15 * normConn;

            if (score < lowestScore) {
                lowestScore = score;
                selectedServer = server;

            }
        }

        return selectedServer;
    }
}