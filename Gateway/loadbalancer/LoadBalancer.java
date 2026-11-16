package Gateway.loadbalancer;

import Gateway.model.BackendServer;

public interface LoadBalancer {
    BackendServer selectServer();

    default BackendServer selectServerForRequest() {
        BackendServer server = selectServer();

        if (server != null) {
            server.increaseConnections();
        }

        return server;
    }
}