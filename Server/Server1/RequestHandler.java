package Server.Server1;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class RequestHandler implements Runnable {
    private final Socket clientSocket;
    private final SystemMonitor systemMonitor;

    public RequestHandler(
            Socket clientSocket,
            SystemMonitor systemMonitor) {
        this.clientSocket = clientSocket;
        this.systemMonitor = systemMonitor;
    }

    @Override
    public void run() {
        try (
                Socket socket = clientSocket;

                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(socket.getInputStream()));

                PrintWriter writer = new PrintWriter(
                        socket.getOutputStream(), true)) {
            String request = reader.readLine();

            if (request == null) {
                return;
            }

            if ("METRICS".equals(request)) {
                SystemMonitor.Metrics metrics = systemMonitor.getCurrentMetrics();

                writer.println(
                        metrics.cpuPercent() + " "
                                + metrics.ramPercent());

            }

            else {
                // Xử lý request bình thường từ Gateway
                Thread.sleep(50000);
                writer.println("Server 1 :Backend đã xử lý: " + request);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}