package Client;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PoolStressTest {
    private static final String GATEWAY_HOST = "localhost";
    private static final int GATEWAY_PORT = 5000;

    public static void main(String[] args) throws Exception {
        System.out.println("========== BẮT ĐẦU TEST CONNECTION POOL ==========");

        // --- TEST 1: GỬI TUẦN TỰ ĐỂ KIỂM TRA IDLE REUSE ---
        System.out.println("\n[Test 1] Gửi 3 request tuần tự (kiểm tra tái sử dụng connection cũ)...");
        for (int i = 1; i <= 3; i++) {
            sendSingleRequest(i);
            Thread.sleep(500); // nghỉ nửa giây để kết nối kịp trả về Idle Queue
        }

        Thread.sleep(2000);

        // --- TEST 2: GỬI ĐỒNG THỜI 15 REQUEST ĐỂ KIỂM TRA SEMAPHORE ---
        int concurrentRequests = 30;
        System.out.println("\n[Test 2] Bắn đồng thời " + concurrentRequests + " requests (kiểm tra Semaphore limit)...");
        ExecutorService executor = Executors.newFixedThreadPool(concurrentRequests);
        CountDownLatch latch = new CountDownLatch(concurrentRequests);

        for (int i = 1; i <= concurrentRequests; i++) {
            final int requestId = i;
            executor.submit(() -> {
                try {
                    sendSingleRequest(requestId);
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();
        System.out.println("\n========== HOÀN THÀNH TẤT CẢ TEST ==========");
    }

    private static void sendSingleRequest(int id) {
        try (Socket socket = new Socket(GATEWAY_HOST, GATEWAY_PORT);
             PrintWriter writer = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

            // Gửi request HTTP cơ bản
            writer.print("GET /OnlineExam/ HTTP/1.1\r\n");
            writer.print("Host: " + GATEWAY_HOST + ":" + GATEWAY_PORT + "\r\n");
            writer.print("Connection: keep-alive\r\n\r\n");
            writer.flush();

            String statusLine = reader.readLine();
            System.out.printf("Request #%02d -> Phản hồi: %s%n", id, statusLine);

        } catch (Exception e) {
            System.out.printf("Request #%02d -> Thất bại: %s%n", id, e.getMessage());
        }
    }
}