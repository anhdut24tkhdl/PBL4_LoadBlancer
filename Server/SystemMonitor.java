package Server;

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
            double processCpu = osBean.getProcessCpuLoad();
            if ((processCpu) < 0 || Double.isNaN(processCpu)) {
                processCpu = 0.0;
            }
            double cpuPercent = Math.max(0.0, Math.min(100.0, Math.round(processCpu * 1000.0) / 10.0));

            Runtime runtime = Runtime.getRuntime();
            long usedMemory = runtime.totalMemory() - runtime.freeMemory();
            long maxMemory = runtime.maxMemory();
             double ramPercent = maxMemory <= 0 ? 0.0 : Math.round((double) usedMemory / maxMemory * 1000.0) / 10.0;

            return new Metrics(cpuPercent, ramPercent);
        } catch (Exception e) {
            return new Metrics(0.0, 0.0);
        }
    }

    public record Metrics(double cpuPercent,double ramPercent) {

    }
}