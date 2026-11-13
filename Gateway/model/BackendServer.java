
package Gateway.model;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class BackendServer {
    private final String name;
    private final String host;
    private final int port;
    private final int weight;

    private volatile boolean alive = true;
    private volatile ServerMetrics metrics = new ServerMetrics();

    private final AtomicInteger activeConnections = new AtomicInteger(0);
    private final AtomicLong totalRequests = new AtomicLong(0);

    // Constructor cũ: mặc định weight = 1
    public BackendServer(String name, String host, int port) {
        this(name, host, port, 1);
    }

    public BackendServer(String name, String host, int port, int weight) {
        if (weight <= 0) {
            throw new IllegalArgumentException("Weight must be greater than 0");
        }

        this.name = name;
        this.host = host;
        this.port = port;
        this.weight = weight;
    }

    public void increaseConnections() {
        activeConnections.incrementAndGet();
        totalRequests.incrementAndGet();
    }

    public void decreaseConnections() {
        activeConnections.updateAndGet(value -> Math.max(0, value - 1));
    }

    public int getActiveConnections() {
        return activeConnections.get();
    }

    public long getTotalRequests() {
        return totalRequests.get();
    }

    public int getWeight() {
        return weight;
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
