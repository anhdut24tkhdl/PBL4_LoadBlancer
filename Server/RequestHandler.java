package Server;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

public class RequestHandler implements Runnable {
    private final Socket clientSocket;
    private final String serverName;
    private final SystemMonitor systemMonitor;

    public RequestHandler(Socket clientSocket, String serverName, SystemMonitor systemMonitor) {
        this.clientSocket = clientSocket;
        this.serverName = serverName;
        this.systemMonitor = systemMonitor;
    }

    @Override
    public void run() {
        try (
                Socket socket = clientSocket;
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                OutputStream output = socket.getOutputStream()
        ) {
            String requestLine = reader.readLine();
            if (requestLine == null || requestLine.isBlank()) {
                return;
            }

            String[] parts = requestLine.split(" ");
            if (parts.length != 3) {
                sendResponse(output, 400, "Bad Request", "Invalid HTTP request");
                return;
            }

            String method = parts[0];
            String target = parts[1];

            while (true) {
                String header = reader.readLine();
                if (header == null || header.isEmpty()) {
                    break;
                }
            }

            if (!"GET".equalsIgnoreCase(method)) {
                sendResponse(output, 405, "Method Not Allowed", "Only GET is supported");
                return;
            }

            String path = target;
            String query = "";
            int questionMark = target.indexOf('?');

            if (questionMark >= 0) {
                path = target.substring(0, questionMark);
                query = target.substring(questionMark + 1);
            }

            if ("/".equals(path)) {
                sendResponse(output, 200, "OK", "Hello from " + serverName);
            } else if ("/test".equals(path)) {
                sendResponse(output, 200, "OK", "Backend test successful: " + serverName);
            } else if ("/delay".equals(path)) {
                int delay = getDelay(query);
                if (delay < 0) {
                    sendResponse(output, 400, "Bad Request", "Invalid delay");
                    return;
                }

                Thread.sleep(delay);
                sendResponse(output, 200, "OK", "Delayed " + delay + " ms by " + serverName);
            } else if ("/metrics".equals(path)) {
                SystemMonitor.Metrics metrics = systemMonitor.getCurrentMetrics();
                String body = "{\"server\":\"" + serverName
                        + "\",\"cpu\":" + metrics.cpuPercent()
                        + ",\"ram\":" + metrics.ramPercent() + "}";

                sendResponse(output, 200, "OK", body, "application/json");
            } else if ("METRICS".equalsIgnoreCase(path)) {
                SystemMonitor.Metrics metrics = systemMonitor.getCurrentMetrics();
                String body = metrics.cpuPercent() + " " + metrics.ramPercent();
                sendResponse(output, 200, "OK", body);
            } else {
                sendResponse(output, 404, "Not Found", "Endpoint not found");
            }
        } catch (Exception e) {
            System.err.println("[" + serverName + "] Request error: " + e.getMessage());
        }
    }

    private int getDelay(String query) {
        if (query == null || query.isEmpty()) {
            return -1;
        }

        for (String parameter : query.split("&")) {
            String[] pair = parameter.split("=");
            if (pair.length == 2 && "ms".equals(pair[0])) {
                try {
                    return Integer.parseInt(URLDecoder.decode(pair[1], StandardCharsets.UTF_8));
                } catch (NumberFormatException e) {
                    return -1;
                }
            }
        }

        return -1;
    }

    private void sendResponse(OutputStream output, int statusCode, String statusText, String body)
            throws Exception {
        sendResponse(output, statusCode, statusText, body, "text/plain; charset=UTF-8");
    }

    private void sendResponse(OutputStream output, int statusCode, String statusText,
                              String body, String contentType) throws Exception {
        byte[] bodyBytes = body.getBytes(StandardCharsets.UTF_8);
        String response = "HTTP/1.1 " + statusCode + " " + statusText + "\r\n"
                + "Content-Type: " + contentType + "\r\n"
                + "Content-Length: " + bodyBytes.length + "\r\n"
                + "X-Backend-Name: " + serverName + "\r\n"
                + "Connection: close\r\n\r\n";

        output.write(response.getBytes(StandardCharsets.UTF_8));
        output.write(bodyBytes);
        output.flush();
    }
}
