package Server.Server1;

import java.net.SocketException;
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

        // Lưu số byte mạng ban đầu
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

    public String printCurrentUsage() {
        long currentTime = System.nanoTime();

        double elapsedSeconds = (currentTime - previousTime) / 1_000_000_000.0;

        if (elapsedSeconds <= 0) {
            return "";
        }

        /*
         * CPU toàn máy
         */
        double cpuPercent = cpu.getSystemCpuLoadBetweenTicks(previousCpuTicks) * 100;

        previousCpuTicks = cpu.getSystemCpuLoadTicks();

        /*
         * RAM
         */
        long totalRam = memory.getTotal();
        long availableRam = memory.getAvailable();
        long usedRam = totalRam - availableRam;

        double ramPercent = (double) usedRam / totalRam * 100;

        double usedRamGB = usedRam / 1024.0 / 1024 / 1024;

        double totalRamGB = totalRam / 1024.0 / 1024 / 1024;

        /*
         * Tốc độ mạng
         */
        long receivedBytes = 0;
        long sentBytes = 0;

        for (NetworkIF network : networkList) {
            if (!isUsableNetwork(network)) {
                continue;
            }

            network.updateAttributes();

            long currentReceived = network.getBytesRecv();
            long currentSent = network.getBytesSent();

            long oldReceived = previousReceived.getOrDefault(
                    network.getName(),
                    currentReceived);

            long oldSent = previousSent.getOrDefault(
                    network.getName(),
                    currentSent);

            long receivedDifference = currentReceived - oldReceived;
            long sentDifference = currentSent - oldSent;

            if (receivedDifference > 0) {
                receivedBytes += receivedDifference;
            }

            if (sentDifference > 0) {
                sentBytes += sentDifference;
            }

            previousReceived.put(
                    network.getName(),
                    currentReceived);

            previousSent.put(
                    network.getName(),
                    currentSent);
        }

        // Chuyển số byte/giây thành megabit/giây
        double downloadMbps = receivedBytes * 8.0 / elapsedSeconds / 1_000_000;

        double uploadMbps = sentBytes * 8.0 / elapsedSeconds / 1_000_000;

        previousTime = currentTime;

        /*
         * Hiển thị kết quả
         */
        String message = "";
        // System.out.printf("CPU đang dùng: %.2f%%%n", cpuPercent);
        message += cpuPercent + " ";
        //
        // System.out.printf(
        // "RAM đang dùng: %.2f%% (%.2f/%.2f GB)%n",
        // ramPercent,
        // usedRamGB,
        // totalRamGB
        // );
        message += (usedRamGB + " ");

        // System.out.printf(
        // "Download hiện tại: %.3f Mbps%n",
        // downloadMbps
        // );
        message += (downloadMbps + " ");

        // System.out.printf(
        // "Upload hiện tại: %.3f Mbps%n",
        // uploadMbps
        // );
        message += uploadMbps + " ";

        // System.out.println("--------------------------------");
        return message;
    }

    private boolean isUsableNetwork(NetworkIF network) {
        network.updateAttributes();

        return network.getIfOperStatus() == NetworkIF.IfOperStatus.UP
                && network.getIfType() != 24;
    }
}