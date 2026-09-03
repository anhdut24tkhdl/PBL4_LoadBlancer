package com.mycompany.server;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.hardware.GlobalMemory;
import oshi.hardware.HardwareAbstractionLayer;
import oshi.hardware.NetworkIF;

public class SystemMonitor {

    private final CentralProcessor cpu;
    private final GlobalMemory memory;
    private final List<NetworkIF> networkList;

    private long[] previousCpuTicks;
    private long previousTime;

    private final Map<String, Long> previousReceived = new HashMap<>();
    private final Map<String, Long> previousSent = new HashMap<>();

    public SystemMonitor() {
        SystemInfo systemInfo = new SystemInfo();
        HardwareAbstractionLayer hardware = systemInfo.getHardware();

        cpu = hardware.getProcessor();
        memory = hardware.getMemory();
        networkList = hardware.getNetworkIFs();

        previousCpuTicks = cpu.getSystemCpuLoadTicks();
        previousTime = System.nanoTime();

        for (NetworkIF network : networkList) {
            network.updateAttributes();

            previousReceived.put(
                    network.getName(),
                    network.getBytesRecv());

            previousSent.put(
                    network.getName(),
                    network.getBytesSent());
        }
    }

    public synchronized Metrics getCurrentMetrics() {
        long currentTime = System.nanoTime();

        double elapsedSeconds = (currentTime - previousTime) / 1_000_000_000.0;

        if (elapsedSeconds <= 0) {
            return new Metrics(0, 0, 0, 0);
        }

        // CPU %
        double cpuPercent = cpu.getSystemCpuLoadBetweenTicks(previousCpuTicks) * 100.0;

        previousCpuTicks = cpu.getSystemCpuLoadTicks();

        // RAM %
        long totalRam = memory.getTotal();
        long availableRam = memory.getAvailable();
        long usedRam = totalRam - availableRam;

        double ramPercent = totalRam == 0
                ? 0
                : usedRam * 100.0 / totalRam;

        // Tốc độ mạng
        long receivedBytes = 0;
        long sentBytes = 0;

        for (NetworkIF network : networkList) {
            network.updateAttributes();

            if (!isUsableNetwork(network)) {
                continue;
            }

            String networkName = network.getName();

            long currentReceived = network.getBytesRecv();
            long currentSent = network.getBytesSent();

            long oldReceived = previousReceived.getOrDefault(
                    networkName,
                    currentReceived);

            long oldSent = previousSent.getOrDefault(
                    networkName,
                    currentSent);

            long receivedDifference = currentReceived - oldReceived;
            long sentDifference = currentSent - oldSent;

            if (receivedDifference > 0) {
                receivedBytes += receivedDifference;
            }

            if (sentDifference > 0) {
                sentBytes += sentDifference;
            }

            previousReceived.put(networkName, currentReceived);
            previousSent.put(networkName, currentSent);
        }

        // Đổi byte/giây thành megabit/giây
        double downloadMbps = receivedBytes * 8.0 / elapsedSeconds / 1_000_000.0;

        double uploadMbps = sentBytes * 8.0 / elapsedSeconds / 1_000_000.0;

        previousTime = currentTime;

        return new Metrics(
                normalizePercent(cpuPercent),
                normalizePercent(ramPercent),
                Math.max(downloadMbps, 0),
                Math.max(uploadMbps, 0));
    }

    private boolean isUsableNetwork(NetworkIF network) {
        return network.getIfOperStatus() == NetworkIF.IfOperStatus.UP
                && network.getIfType() != 24;
    }

    private double normalizePercent(double value) {
        if (Double.isNaN(value) || value < 0) {
            return 0;
        }

        return Math.min(value, 100);
    }

    public record Metrics(
            double cpuPercent,
            double ramPercent,
            double downloadMbps,
            double uploadMbps) {
    }
}