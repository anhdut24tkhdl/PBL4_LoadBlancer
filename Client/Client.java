package Client;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class Client {
    private static final String GATEWAY_HOST = "localhost";
    private static final int GATEWAY_PORT = 5000;

    public void sendRequest() {
        try (
                Socket socket = new Socket(GATEWAY_HOST, GATEWAY_PORT);
                BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                PrintWriter writer = new PrintWriter(socket.getOutputStream(), true)) {

            // Gửi HTTP Request chuẩn
            writer.print("GET / HTTP/1.1\r\n");
            writer.print("Host: " + GATEWAY_HOST + ":" + GATEWAY_PORT + "\r\n");
            writer.print("User-Agent: LB-Client/1.0\r\n");
            writer.print("Connection: close\r\n");
            writer.print("\r\n");
            writer.flush();

            // Đọc response từ Gateway
            String statusLine = reader.readLine();
            String serverName = "Unknown Server";
            String line;
            while ((line = reader.readLine()) != null && !line.isEmpty()) {
                if (line.toLowerCase().startsWith("x-backend-server:")) {
                    serverName = line.substring("x-backend-server:".length()).trim();
                }
            }

            System.out.println("[Client] Phản hồi: " + statusLine + " | Xử lý bởi: " + serverName);

        } catch (Exception e) {
            System.err.println("[Client] Không thể kết nối Gateway: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        Client client = new Client();
        System.out.println("=== Gửi request đơn lẻ thử nghiệm ===");
        client.sendRequest();
    }
}