
package Gateway.loadbalancer;

import Gateway.model.BackendServer;
import Gateway.model.ServerMetrics;
import Gateway.registry.ServerRegistry;

import java.util.ArrayList;
import java.util.List;

public class AdaptiveLoadBalancer implements LoadBalancer {

    private final ServerRegistry registry;

    public enum SystemState {
        NORMAL,
        HIGH_CPU,
        HIGH_RAM,
        HIGH_CONCURRENCY,
        HIGH_LATENCY,
        CRITICAL
    }

    private volatile SystemState currentState = SystemState.NORMAL;

    // Hysteresis
    private static final double CPU_HIGH = 75.0;
    private static final double CPU_LOW = 65.0;

    private static final double RAM_HIGH = 80.0;
    private static final double RAM_LOW = 70.0;

    private static final double LATENCY_HIGH = 400.0;
    private static final double LATENCY_LOW = 250.0;

    private static final double CONN_HIGH = 35.0;
    private static final double CONN_LOW = 20.0;

    private static final double SERVER_CPU_LIMIT = 85.0;
    private static final double SERVER_RAM_LIMIT = 90.0;

    private static final double MAX_LATENCY_MS = 1000.0;
    private static final double MAX_CONNECTIONS = 50.0;
    private static final double EPSILON = 1e-9;

    private int tieBreaker = 0;

    //Theo dõi cảnh báo riêng cho từng chỉ số
    private boolean cpuAlert;
    private boolean ramAlert;
    private boolean latencyAlert;
    private boolean connectionAlert;

    public AdaptiveLoadBalancer(ServerRegistry registry) {
        this.registry = registry;
    }

    @Override
    public synchronized BackendServer selectServer() {

        List<BackendServer> servers = registry.getServers();

        if (servers == null || servers.isEmpty()) {
            return null;
        }

        // BƯỚC 1: Thu thập metric trung bình của cụm
        double totalCpu = 0;
        double totalRam = 0;
        double totalLatency = 0;
        double totalConn = 0;
        int activeCount = 0;

        for (BackendServer server : servers) {
            if (!server.isAlive() || server.getMetrics() == null) {
                continue;
            }

            ServerMetrics m = server.getMetrics();

            totalCpu += safePercent(m.getCpuUsage());
            totalRam += safePercent(m.getRamUsage());
            totalLatency += safeLatency(m.getLatency());
            totalConn += server.getActiveConnections();

            activeCount++;
        }

        if (activeCount == 0) {
            return null;
        }

        double avgCpu = totalCpu / activeCount;
        double avgRam = totalRam / activeCount;
        double avgLatency = totalLatency / activeCount;
        double avgConn = totalConn / activeCount;

        // BƯỚC 2: Xác định trạng thái hệ thống
        updateSystemState(
                avgCpu, avgRam, avgLatency, avgConn
        );

        // BƯỚC 3: Điều chỉnh trọng số
        double wCpu, wRam, wLatency, wConn;

        switch (currentState) {
            case HIGH_CPU:
                wCpu = 0.50;
                wRam = 0.20;
                wLatency = 0.15;
                wConn = 0.15;
                break;

            case HIGH_RAM:
                wCpu = 0.20;
                wRam = 0.50;
                wLatency = 0.15;
                wConn = 0.15;
                break;

            case HIGH_CONCURRENCY:
                wCpu = 0.20;
                wRam = 0.15;
                wLatency = 0.20;
                wConn = 0.45;
                break;

            case HIGH_LATENCY:
                wCpu = 0.15;
                wRam = 0.15;
                wLatency = 0.45;
                wConn = 0.25;
                break;

            case CRITICAL:
                wCpu = 0.30;
                wRam = 0.30;
                wLatency = 0.20;
                wConn = 0.20;
                break;

            case NORMAL:
            default:
                wCpu = 0.30;
                wRam = 0.25;
                wLatency = 0.25;
                wConn = 0.20;
                break;
        }

        // BƯỚC 4: Tìm các server có score thấp nhất
        double lowestScore = Double.MAX_VALUE;

        List<BackendServer> candidates = new ArrayList<>();

        for (BackendServer server : servers) {

            if (!server.isAlive() || server.getMetrics() == null) {
                continue;
            }

            ServerMetrics m = server.getMetrics();

            double normCpu = safePercent(m.getCpuUsage());
            double normRam = safePercent(m.getRamUsage());

            double normLatency = safeLatency(m.getLatency()) / MAX_LATENCY_MS * 100.0;
            double normConn = server.getActiveConnections() / MAX_CONNECTIONS * 100.0;

            double score =
                    wCpu * normCpu
                    + wRam * normRam
                    + wLatency * normLatency
                    + wConn * normConn;

            // Penalty cho server quá tải
            if (normCpu > SERVER_CPU_LIMIT) {
                score += 50;
            }

            if (normRam > SERVER_RAM_LIMIT) {
                score += 50;
            }

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

        // BƯỚC 5: Round Robin nếu có nhiều server bằng điểm
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

    // Metric CPU/RAM chưa có dữ liệu -> dùng 50% trung tính
    private double safePercent(double value) {
        if (!Double.isFinite(value) || value < 0) {
            return 50.0;
        }

        return Math.min(100.0, value);
    }

    // Latency chưa có dữ liệu -> dùng 250 ms trung tính
    private double safeLatency(double value) {
        if (!Double.isFinite(value) || value < 0) {
            return LATENCY_LOW;
        }

        return value;
    }

    private boolean updateAlert(boolean wasActive, double value,
                            double lowThreshold, double highThreshold) {
        if (wasActive) {
            return value > lowThreshold;
        }
        return value >= highThreshold;
    }

    private void updateSystemState(double avgCpu, double avgRam,
                               double avgLatency, double avgConn) {
        cpuAlert = updateAlert(cpuAlert, avgCpu, CPU_LOW, CPU_HIGH);
        ramAlert = updateAlert(ramAlert, avgRam, RAM_LOW, RAM_HIGH);
        latencyAlert = updateAlert(latencyAlert, avgLatency, LATENCY_LOW, LATENCY_HIGH);
        connectionAlert = updateAlert(connectionAlert, avgConn, CONN_LOW, CONN_HIGH);

        int alerts = 0;
        if (cpuAlert) alerts++;
        if (ramAlert) alerts++;
        if (latencyAlert) alerts++;
        if (connectionAlert) alerts++;

        if (alerts >= 2) {
            currentState = SystemState.CRITICAL;
        } else if (cpuAlert) {
            currentState = SystemState.HIGH_CPU;
        } else if (ramAlert) {
            currentState = SystemState.HIGH_RAM;
        } else if (latencyAlert) {
            currentState = SystemState.HIGH_LATENCY;
        } else if (connectionAlert) {
            currentState = SystemState.HIGH_CONCURRENCY;
        } else {
            currentState = SystemState.NORMAL;
        }
    }

    public SystemState getCurrentState() {
        return currentState;
    }
}
