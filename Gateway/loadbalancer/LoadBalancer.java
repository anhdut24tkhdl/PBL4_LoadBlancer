package Gateway.loadbalancer;

import Gateway.model.BackendServer;

public interface LoadBalancer {
    BackendServer selectServer();
}