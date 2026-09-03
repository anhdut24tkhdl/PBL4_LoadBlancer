package Gateway.loadbalancer;

import java.net.Socket;

import Gateway.GetMonitor;

public class DynamicLoadBalancer {

    public int getPortServer(Socket server1, Socket server2) {
        GetMonitor getMonitor = new GetMonitor(server1);
        GetMonitor getMonitor1 = new GetMonitor(server2);
        GetMonitor.Metrics metrics = getMonitor.getMetrics();
        GetMonitor.Metrics metrics1 = getMonitor1.getMetrics();

        if (0.35 * metrics.cpuPercent() + 0.3 * metrics.ramPercent() > 0.35 * metrics1.cpuPercent()
                + 0.3 * metrics1.ramPercent()) {
            return server2.getPort();
        } else
            return server1.getPort();

    }

}
