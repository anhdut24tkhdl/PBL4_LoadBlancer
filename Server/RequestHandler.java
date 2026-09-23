package Server;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;

public class RequestHandler implements Runnable {
    private final Socket clientSocket;
    private final SystemMonitor systemMonitor;

    public RequestHandler(
            Socket clientSocket, String name,
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
                Path file = Path.of("D:\\HK1_2026_2027\\Web\\Homework\\banthan.htm");

                if (!Files.exists(file)) {
                    writer.print(
                            "HTTP/1.1 404 Not Found\r\n" +
                                    "Content-Type: text/plain; charset=UTF-8\r\n" +
                                    "Connection: close\r\n" +
                                    "\r\n" +
                                    "Khong tim thay index.html");
                    writer.flush();
                    return;
                }

                byte[] htmlBytes = Files.readAllBytes(file);

                String header = "HTTP/1.1 200 OK\r\n" +
                        "Content-Type: text/html; charset=UTF-8\r\n" +
                        "Content-Length: " + htmlBytes.length + "\r\n" +
                        "Connection: close\r\n" +
                        "\r\n";

                socket.getOutputStream().write(header.getBytes("UTF-8"));
                socket.getOutputStream().write(htmlBytes);
                socket.getOutputStream().flush();
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}