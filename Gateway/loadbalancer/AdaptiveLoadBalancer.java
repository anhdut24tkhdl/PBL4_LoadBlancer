package Gateway.loadbalancer;

import Gateway.model.BackendServer;
import Gateway.model.ServerMetrics;
import Gateway.registry.ServerRegistry;
import java.util.List;

/**
 * Thuật toán Adaptive Load Balancing toàn diện với cơ chế Hysteresis:
 * Giám sát đầy đủ cả 4 chỉ số: CPU, RAM, Latency và Active Connections.
 */
public class AdaptiveLoadBalancer implements LoadBalancer {

    private final ServerRegistry registry;

    // Bộ trạng thái mở rộng cho toàn bộ các thông số
    public enum SystemState {
        NORMAL,           // Trạng thái bình thường
        HIGH_CPU,         // Hệ thống đang nghẽn CPU
        HIGH_RAM,         // Hệ thống đang chịu áp lực bộ nhớ (nguy cơ OOM)
        HIGH_CONCURRENCY, // Hệ thống đang quá tải số lượng kết nối đồng thời
        HIGH_LATENCY,     // Hệ thống phản hồi chậm / nghẽn mạng
        CRITICAL          // Đa quá tải (nhiều chỉ số cùng vượt ngưỡng)
    }

    private SystemState currentState = SystemState.NORMAL;

    // --- CÁC CẶP NGƯỠNG HYSTERESIS (HIGH / LOW) ---
    // 1. CPU (%)
    private static final double CPU_HIGH = 75.0;
    private static final double CPU_LOW  = 65.0;

    // 2. RAM (%)
    private static final double RAM_HIGH = 80.0;
    private static final double RAM_LOW  = 70.0;

    // 3. Latency (ms)
    private static final double LATENCY_HIGH = 400.0;
    private static final double LATENCY_LOW  = 250.0;

    // 4. Active Connections trung bình (dựa trên chuẩn 50 conn max)
    private static final double CONN_HIGH = 35.0;
    private static final double CONN_LOW  = 20.0;

    // Ngưỡng phạt cá nhân cho từng server bị nguy hiểm (Hard Limit)
    private static final double SERVER_CPU_LIMIT = 85.0;
    private static final double SERVER_RAM_LIMIT = 90.0;

    public AdaptiveLoadBalancer(ServerRegistry registry) {
        this.registry = registry;
    }

    @Override
    public synchronized BackendServer selectServer() {
        List<BackendServer> servers = registry.getServers();
        if (servers == null || servers.isEmpty()) {
            return null;
        }

        // Bước 1: Tính trung bình cộng của cả 4 chỉ số trên toàn cụm
        double totalCpu = 0.0;
        double totalRam = 0.0;
        double totalLatency = 0.0;
        double totalConn = 0.0;
        int activeCount = 0;

        for (BackendServer server : servers) {
            if (server.isAlive() && server.getMetrics() != null) {
                totalCpu += server.getMetrics().getCpuUsage();
                totalRam += server.getMetrics().getRamUsage();
                totalLatency += server.getMetrics().getLatency();
                totalConn += server.getActiveConnections();
                activeCount++;
            }
        }

        double avgCpu = activeCount > 0 ? totalCpu / activeCount : 0.0;
        double avgRam = activeCount > 0 ? totalRam / activeCount : 0.0;
        double avgLatency = activeCount > 0 ? totalLatency / activeCount : 0.0;
        double avgConn = activeCount > 0 ? totalConn / activeCount : 0.0;

        // Bước 2: Cập nhật trạng thái hệ thống theo nguyên tắc Hysteresis
        updateSystemState(avgCpu, avgRam, avgLatency, avgConn);

        // Bước 3: Điều chỉnh trọng số (Adaptive Weights) tương ứng với từng trạng thái
        double wCpu, wRam, wLatency, wConn;

        switch (currentState) {
            case HIGH_CPU:
                // Ưu tiên CPU cao nhất
                wCpu = 0.50; wRam = 0.20; wLatency = 0.15; wConn = 0.15;
                break;

            case HIGH_RAM:
                // Tránh tràn RAM / OOM, ưu tiên đưa việc cho server còn nhiều RAM
                wCpu = 0.20; wRam = 0.50; wLatency = 0.15; wConn = 0.15;
                break;

            case HIGH_CONCURRENCY:
                // Quá tải số lượng connection, thuật toán tiệm cận Least Connection
                wCpu = 0.20; wRam = 0.15; wLatency = 0.20; wConn = 0.45;
                break;

            case HIGH_LATENCY:
                // Mạng trễ hoặc hàng đợi phản hồi chậm
                wCpu = 0.15; wRam = 0.15; wLatency = 0.45; wConn = 0.25;
                break;

            case CRITICAL:
                // Cả hệ thống gồng mình: Phân bổ đều và siết chặt kiểm tra
                wCpu = 0.30; wRam = 0.30; wLatency = 0.20; wConn = 0.20;
                break;

            case NORMAL:
            default:
                // Trọng số tiêu chuẩn khi hệ thống ổn định
                wCpu = 0.30; wRam = 0.25; wLatency = 0.25; wConn = 0.20;
                break;
        }

        // Bước 4: Chấm điểm từng server và tìm node tối ưu
        BackendServer bestServer = null;
        double lowestScore = Double.MAX_VALUE;

        for (BackendServer server : servers) {
            if (!server.isAlive()) {
                continue;
            }

            ServerMetrics metrics = server.getMetrics();
            if (metrics == null) {
                continue;
            }

            // Chuẩn hóa về thang 0 - 100
            double normCpu = metrics.getCpuUsage();
            double normRam = metrics.getRamUsage();
            double normLatency = Math.min(100.0, (metrics.getLatency() / 1000.0) * 100.0);
            double normConn = Math.min(100.0, (server.getActiveConnections() / 50.0) * 100.0);

            double score = (wCpu * normCpu)
                         + (wRam * normRam)
                         + (wLatency * normLatency)
                         + (wConn * normConn);

            // Điểm phạt (Penalty) nếu server riêng lẻ chạm ngưỡng nguy kịch
            if (normCpu > SERVER_CPU_LIMIT) {
                score += 50.0;
            }
            if (normRam > SERVER_RAM_LIMIT) {
                score += 50.0;
            }

            if (score < lowestScore) {
                lowestScore = score;
                bestServer = server;
            }
        }

        return bestServer;
    }

    /**
     * Quản lý chuyển đổi trạng thái bằng Hysteresis cho cả 4 chỉ số.
     */
    private void updateSystemState(double avgCpu, double avgRam, double avgLatency, double avgConn) {
        // Đếm xem có bao nhiêu chỉ số đang vượt ngưỡng báo động cao
        int highAlertCount = 0;
        if (avgCpu >= CPU_HIGH) highAlertCount++;
        if (avgRam >= RAM_HIGH) highAlertCount++;
        if (avgLatency >= LATENCY_HIGH) highAlertCount++;
        if (avgConn >= CONN_HIGH) highAlertCount++;

        // Nếu từ 2 chỉ số trở lên cùng báo động -> Chuyển sang CRITICAL
        if (highAlertCount >= 2) {
            currentState = SystemState.CRITICAL;
            return;
        }

        // Xử lý chuyển đổi theo từng trạng thái cụ thể với Hysteresis
        switch (currentState) {
            case NORMAL:
                if (avgCpu >= CPU_HIGH) {
                    currentState = SystemState.HIGH_CPU;
                } else if (avgRam >= RAM_HIGH) {
                    currentState = SystemState.HIGH_RAM;
                } else if (avgLatency >= LATENCY_HIGH) {
                    currentState = SystemState.HIGH_LATENCY;
                } else if (avgConn >= CONN_HIGH) {
                    currentState = SystemState.HIGH_CONCURRENCY;
                }
                break;

            case HIGH_CPU:
                // Chỉ thoát về NORMAL khi CPU tụt hẳn xuống dưới ngưỡng thấp
                if (avgCpu <= CPU_LOW) {
                    currentState = SystemState.NORMAL;
                }
                break;

            case HIGH_RAM:
                // Chỉ thoát về NORMAL khi RAM tụt hẳn xuống dưới ngưỡng thấp
                if (avgRam <= RAM_LOW) {
                    currentState = SystemState.NORMAL;
                }
                break;

            case HIGH_LATENCY:
                // Chỉ thoát về NORMAL khi Latency tụt hẳn xuống dưới ngưỡng thấp
                if (avgLatency <= LATENCY_LOW) {
                    currentState = SystemState.NORMAL;
                }
                break;

            case HIGH_CONCURRENCY:
                // Chỉ thoát về NORMAL khi số kết nối hạ xuống an toàn
                if (avgConn <= CONN_LOW) {
                    currentState = SystemState.NORMAL;
                }
                break;

            case CRITICAL:
                // Chỉ thoát CRITICAL khi số chỉ số nguy hiểm giảm xuống dưới 1
                if (highAlertCount < 1) {
                    currentState = SystemState.NORMAL;
                }
                break;
        }
    }

    public SystemState getCurrentState() {
        return currentState;
    }
}