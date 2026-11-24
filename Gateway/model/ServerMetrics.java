
package Gateway.model;

public class ServerMetrics {

    private static final double LATENCY_ALPHA = 0.2;

    // -1 nghĩa là chưa thu thập được dữ liệu
    private volatile double cpuUsage = -1.0;
    private volatile double ramUsage = -1.0;
    private volatile double latency = -1.0;
    private volatile double networkUsage = -1.0;

    // Hằng số thời gian 10 giây. Sau 10 giây, ảnh hưởng của số đo cũ
    // còn khoảng e^-1, tương đương 36,8%.
    private static final double PEAK_DECAY_NANOS = 10_000_000_000.0;
    private static final double MIN_LATENCY_MS = 1.0;

    private double peakLatencyMs = -1.0;
    private long lastPeakUpdateNanos;

    public double getCpuUsage() {
        return cpuUsage;
    }

    public void setCpuUsage(double cpuUsage) {
        this.cpuUsage = cpuUsage;
    }

    public double getRamUsage() {
        return ramUsage;
    }

    public void setRamUsage(double ramUsage) {
        this.ramUsage = ramUsage;
    }

    public double getLatency() {
        return latency;
    }

    public void setLatency(double latency) {
        this.latency = latency;
    }

    // Ghi nhận một mẫu độ trễ và cập nhật EWMA
    public synchronized void recordLatencySample(double sampleMs) {
        if (!Double.isFinite(sampleMs) || sampleMs < 0) {
            return;
        }

        // Mẫu đầu tiên
        if (latency < 0) {
            latency = sampleMs;
            return;
        }

        // Các mẫu tiếp theo
        latency = LATENCY_ALPHA * sampleMs
                + (1 - LATENCY_ALPHA) * latency;
    }

    public double getNetworkUsage() {
        return networkUsage;
    }

    public void setNetworkUsage(double networkUsage) {
        this.networkUsage = networkUsage;
    }

    private double remainingWeight(long elapsedNanos) {
        return Math.exp(-Math.max(0L, elapsedNanos) / PEAK_DECAY_NANOS);
    }

    public synchronized void recordPeakLatencySample(double sampleMs) {
        if (!Double.isFinite(sampleMs) || sampleMs < 0.0) {
            return;
        }

        long nowNanos = System.nanoTime();
        sampleMs = Math.max(MIN_LATENCY_MS, sampleMs);

        if (peakLatencyMs < 0.0 || sampleMs >= peakLatencyMs) {
            // Mẫu đầu tiên hoặc một đỉnh chậm mới.
            peakLatencyMs = sampleMs;
        } else {
            // Khi backend nhanh trở lại, giảm ước lượng từ từ.
            double oldWeight = remainingWeight(nowNanos - lastPeakUpdateNanos);
            peakLatencyMs = oldWeight * peakLatencyMs
                    + (1.0 - oldWeight) * sampleMs;
        }

        lastPeakUpdateNanos = nowNanos;
    }

    public synchronized double getPeakLatencyMs(long nowNanos) {
        if (peakLatencyMs < 0.0) {
            return -1.0;
        }

        // Backend ít được chọn vẫn có cơ hội nhận request trở lại.
        // Việc đọc điểm không làm thay đổi thời điểm của mẫu gần nhất.
        double oldWeight = remainingWeight(nowNanos - lastPeakUpdateNanos);
        return MIN_LATENCY_MS + (peakLatencyMs - MIN_LATENCY_MS) * oldWeight;
    }

    public synchronized void resetPeakLatency() {
        peakLatencyMs = -1.0;
    }
}
