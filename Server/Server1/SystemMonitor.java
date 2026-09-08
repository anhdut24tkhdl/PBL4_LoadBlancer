package Server.Server1;

import com.sun.management.OperatingSystemMXBean;
import java.lang.management.ManagementFactory;

public class SystemMonitor {

    private final OperatingSystemMXBean osBean;

    public SystemMonitor() {
        OperatingSystemMXBean bean = null;
        try {
            java.lang.management.OperatingSystemMXBean baseBean = ManagementFactory.getOperatingSystemMXBean();
            if (baseBean instanceof OperatingSystemMXBean) {
                bean = (OperatingSystemMXBean) baseBean;
            }
        } catch (Exception e) {
            System.err.println("[Server 1] Cảnh báo: Không thể lấy OperatingSystemMXBean: " + e.getMessage());
        }
        this.osBean = bean;
    }

    public synchronized Metrics getCurrentMetrics() {
        if (osBean == null) {
            return new Metrics(0.0, 0.0);
        }

        try {
            // Đo % CPU (từ 0.0 đến 1.0 -> nhân 100)
            double cpuLoad = osBean.getCpuLoad();
            if (cpuLoad < 0 || Double.isNaN(cpuLoad)) {
                cpuLoad = 0.0;
            }
            double cpuPercent = Math.max(0.0, Math.min(100.0, Math.round(cpuLoad * 1000.0) / 10.0));

            // Đo % RAM
            long totalRam = osBean.getTotalMemorySize();
            long freeRam = osBean.getFreeMemorySize();
            long usedRam = totalRam - freeRam;

            double ramPercent = totalRam <= 0
                    ? 0.0
                    : Math.max(0.0, Math.min(100.0, Math.round(usedRam * 1000.0 / totalRam) / 10.0));

            return new Metrics(cpuPercent, ramPercent);
        } catch (Exception e) {
            return new Metrics(0.0, 0.0);
        }
    }

    public record Metrics(
            double cpuPercent,
            double ramPercent) {
    }
}