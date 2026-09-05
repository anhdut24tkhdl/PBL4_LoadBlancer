package Gateway.registry;

import Gateway.model.BackendServer;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class ServerRegistry {
    private final List<BackendServer> servers = new CopyOnWriteArrayList<>();

    public void addServer(BackendServer server) {
        servers.add(server);
    }

    public void removeServer(BackendServer server) {
        servers.remove(server);
    }

    public List<BackendServer> getServers() {
        return servers;
    }
}