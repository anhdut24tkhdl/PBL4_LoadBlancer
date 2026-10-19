package Server;

import com.sun.management.OperatingSystemMXBean;
import java.lang.management.ManagementFactory;

public class SystemMonitor {
    private final OperatingSystemMXBean osBean;

    public SystemMonitor() {
        java.lang.management.OperatingSystemMXBean bean =
                ManagementFactory.getOperatingSystemMXBean();
        osBean = bean instanceof OperatingSystemMXBean
                ? (OperatingSystemMXBean) bean
                : null;
    }

    public synchronized Metrics getCurrentMetrics() {
        if (osBean == null) {
            return new Metrics(0.0, 0.0);
        }

        try {
            double processCpu = osBean.getProcessCpuLoad();
            if (processCpu < 0 || Double.isNaN(processCpu)) {
                processCpu = 0.0;
            }

            double cpuPercent = Math.round(processCpu * 1000.0) / 10.0;
            Runtime runtime = Runtime.getRuntime();
            long usedMemory = runtime.totalMemory() - runtime.freeMemory();
            long maxMemory = runtime.maxMemory();

            double ramPercent = maxMemory <= 0
                    ? 0.0
                    : (double) usedMemory / maxMemory * 100.0;
            ramPercent = Math.round(ramPercent * 10.0) / 10.0;

            return new Metrics(cpuPercent, ramPercent);
        } catch (Exception e) {
            return new Metrics(0.0, 0.0);
        }
    }

    public record Metrics(double cpuPercent, double ramPercent) {
    }
}
