package Client;

import Gateway.protocol.Request;
import Gateway.protocol.Response;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class ParallelStressTest {
    public static void main(String[] args) {
        System.out.println("=== BẮN 30 REQUEST ĐỒNG THỜI CÙNG 1 LÚC ===");

        for (int i = 1; i <= 50; i++) {
            final int id = i;
            
            // 👉 TẠO LUỒNG MỚI CHO MỖI REQUEST (KHÔNG AI PHẢI CHỜ AI)
            new Thread(() -> {
                try (Socket socket = new Socket("localhost", 5000);
                     BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                     PrintWriter writer = new PrintWriter(socket.getOutputStream(), true)) {

                    System.out.println("[Client #" + id + "] Đã gửi request vào Gateway!");
                    Request req = new Request("CALCULATE", "Req-" + id, "Dữ liệu #" + id);
                    writer.println(req.serialize());

                    // Luồng này sẽ đứng chờ 20s một cách độc lập
                    Response res = Response.parse(reader.readLine());
                    System.out.println("[Client #" + id + "] <- Hoàn thành từ " + res.getServerName());

                } catch (Exception e) {
                    System.err.println("[Client #" + id + "] Lỗi: " + e.getMessage());
                }
            }).start(); // Bắn ngay lập tức!
        }
    }
}