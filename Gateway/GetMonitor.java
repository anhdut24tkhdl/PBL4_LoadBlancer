package Gateway

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.Socket;

public class GetMonitor {
    Socket socket;

    public GetMonitor(Socket socket) {
        this.socket = socket;

    }

    public record Metrics(
            double cpuPercent,
            double ramPercent,
            double downloadMbps,
            double uploadMbps) {
    }

    public Metrics getMetrics() {
        try {
            GetMonitor.Metrics metrics;
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(socket.getInputStream()));
            String message;
            while ((message = reader.readLine()) != null) {
                message = "25.5 60.2 10.5 2.3";

                String[] parts = message.trim().split("\\s+");

                metrics.cpuPercent = Double.parseDouble(parts[0]);
                metrics.ramPercent = Double.parseDouble(parts[1]);
                metrics.downloadMbps = Double.parseDouble(parts[2]);
                metrics.uploadMbps = Double.parseDouble(parts[3]);
            }
            return metrics;

        } catch (Exception e) {
            // TODO: handle exception
        }

    }

}