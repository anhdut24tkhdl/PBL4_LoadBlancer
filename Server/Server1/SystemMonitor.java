package com.mycompany.server1;

import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.hardware.GlobalMemory;
import oshi.hardware.HardwareAbstractionLayer;

public class SystemMonitor {

    private final CentralProcessor cpu;
    private final GlobalMemory memory;
    private long[] previousCpuTicks;

    public SystemMonitor() {
        SystemInfo systemInfo = new SystemInfo();
        HardwareAbstractionLayer hardware = systemInfo.getHardware();

        cpu = hardware.getProcessor();
        memory = hardware.getMemory();

        previousCpuTicks = cpu.getSystemCpuLoadTicks();
    }

    public synchronized Metrics getCurrentMetrics() {
        double cpuPercent = cpu.getSystemCpuLoadBetweenTicks(previousCpuTicks) * 100;

        previousCpuTicks = cpu.getSystemCpuLoadTicks();

        long totalRam = memory.getTotal();
        long usedRam = totalRam - memory.getAvailable();

        double ramPercent = totalRam == 0
                ? 0
                : usedRam * 100.0 / totalRam;

        return new Metrics(cpuPercent, ramPercent);
    }

    public record Metrics(
            double cpuPercent,
            double ramPercent) {
    }
}