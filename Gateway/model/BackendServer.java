package Gateway.model;

import java.util.concurrent.atomic.AtomicInteger;

public class BackendServer {
    private final String name;
    private final String host;
    private final int port;

    private volatile boolean alive = true;
    private volatile ServerMetrics metrics = new ServerMetrics();

    private final AtomicInteger activeConnections = new AtomicInteger(0);

    public BackendServer(String name, String host, int port) {
        this.name = name;
        this.host = host;
        this.port = port;
    }

    public void increaseConnections() {
        activeConnections.incrementAndGet();
    }

    public void decreaseConnections() {
        activeConnections.decrementAndGet();
    }

    public int getActiveConnections() {
        return activeConnections.get();
    }

    public String getName() {
        return name;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public boolean isAlive() {
        return alive;
    }

    public void setAlive(boolean alive) {
        this.alive = alive;
    }

    public ServerMetrics getMetrics() {
        return metrics;
    }

    public void setMetrics(ServerMetrics metrics) {
        this.metrics = metrics;
    }
}