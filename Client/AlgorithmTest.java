package Client;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Lớp kiểm thử chuyên sâu cho 2 thuật toán:
 * 1. Round Robin: Kiểm tra tính tuần tự xoay vòng đều giữa các Backend Server.
 * 2. Least Connection: Kiểm tra việc điều hướng request vào Server có ít kết nối đang xử lý nhất.
 */
public class AlgorithmTest {
    private static final String GATEWAY_HOST = "localhost";
    private static final int GATEWAY_PORT = 5000;

    public static void main(String[] args) throws Exception {
        System.out.println("===============================================================");
        System.out.println("         CHƯƠNG TRÌNH KIỂM THỬ THUẬT TOÁN CÂN BẰNG TẢI");
        System.out.println("===============================================================\n");

        if (args.length > 0 && args[0].equalsIgnoreCase("least_connection")) {
            testLeastConnection();
        } else {
            testRoundRobin();
        }
    }

    public static void testRoundRobin() throws Exception {
        int totalRequests = 12;
        System.out.println("--- [TEST 1: ROUND ROBIN - KIỂM TRA XOAY VÒNG TUẦN TỰ] ---");
        System.out.println("Gửi " + totalRequests + " requests tuần tự...\n");

        Map<String, AtomicInteger> countMap = new ConcurrentHashMap<>();

        for (int i = 1; i <= totalRequests; i++) {
            String server = sendHttpRequest("/?req=" + i);
            countMap.computeIfAbsent(server, k -> new AtomicInteger(0)).incrementAndGet();
            System.out.printf("Request #%02d -> Điều phối tới: [%s]%n", i, server);
            Thread.sleep(100);
        }

        System.out.println("\n--- Thống kê phân phối Round Robin ---");
        countMap.forEach((srv, cnt) -> {
            System.out.printf("  + %-15s: %d requests%n", srv, cnt.get());
        });
        System.out.println("===============================================================\n");
    }

    public static void testLeastConnection() throws Exception {
        System.out.println("--- [TEST 2: LEAST CONNECTION - KIỂM TRA ĐIỀU HƯỚNG THEO SỐ KẾT NỐI] ---");
        System.out.println("Mô phỏng:");
        System.out.println("  1. Gửi 2 request chậm (xử lý trong 3 giây) tới Gateway để chiếm giữ kết nối.");
        System.out.println("  2. Ngay sau đó, gửi tiếp các request nhanh.");
        System.out.println("  3. Quan sát: Các request nhanh sẽ được định tuyến sang Server có ít kết nối nhất!\n");

        CountDownLatch slowLatch = new CountDownLatch(2);
        CountDownLatch quickLatch = new CountDownLatch(4);

        // Bắn 2 slow request
        new Thread(() -> {
            System.out.println("[Slow Req 1] Đang gửi (chiếm giữ kết nối 3s)...");
            String srv = sendHttpRequest("/?sleep=3000");
            System.out.println("[Slow Req 1] <- Hoàn tất từ " + srv);
            slowLatch.countDown();
        }).start();

        Thread.sleep(200);

        new Thread(() -> {
            System.out.println("[Slow Req 2] Đang gửi (chiếm giữ kết nối 3s)...");
            String srv = sendHttpRequest("/?sleep=3000");
            System.out.println("[Slow Req 2] <- Hoàn tất từ " + srv);
            slowLatch.countDown();
        }).start();

        // Chờ 500ms để Gateway đã phân phối 2 slow request vào 2 server
        Thread.sleep(500);
        System.out.println("\n>>> Bắn tiếp 4 request nhanh khi 2 server kia đang bận...");

        for (int i = 1; i <= 4; i++) {
            final int id = i;
            new Thread(() -> {
                String srv = sendHttpRequest("/?quick=" + id);
                System.out.printf("[Quick Req %d] -> Được chuyển tới: [%s] (vì server này rảnh/ít kết nối nhất)%n", id, srv);
                quickLatch.countDown();
            }).start();
            Thread.sleep(100);
        }

        quickLatch.await();
        slowLatch.await();
        System.out.println("\n=== Hoàn tất kiểm thử Least Connection ===");
    }

    private static String sendHttpRequest(String path) {
        try (Socket socket = new Socket(GATEWAY_HOST, GATEWAY_PORT);
             BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter writer = new PrintWriter(socket.getOutputStream(), true)) {

            writer.print("GET " + path + " HTTP/1.1\r\n");
            writer.print("Host: " + GATEWAY_HOST + ":" + GATEWAY_PORT + "\r\n");
            writer.print("Connection: close\r\n\r\n");
            writer.flush();

            String line;
            while ((line = reader.readLine()) != null && !line.isEmpty()) {
                if (line.toLowerCase().startsWith("x-backend-server:")) {
                    return line.substring("x-backend-server:".length()).trim();
                }
            }
            return "Unknown Server";
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }
}
