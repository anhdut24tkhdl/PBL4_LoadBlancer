package Client;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

public class ParallelStressTest {
    private static final String GATEWAY_HOST = "localhost";
    private static final int GATEWAY_PORT = 5000;
    private static final int TOTAL_REQUESTS = 30;

    public static void main(String[] args) throws InterruptedException {
        System.out.println("=================================================");
        System.out.println("   BẮN " + TOTAL_REQUESTS + " REQUESTS ĐỒNG THỜI ĐỂ TEST CÂN BẰNG TẢI");
        System.out.println("=================================================");

        Map<String, AtomicInteger> stats = new ConcurrentHashMap<>();
        CountDownLatch latch = new CountDownLatch(TOTAL_REQUESTS);
        long startTime = System.currentTimeMillis();

        for (int i = 1; i <= TOTAL_REQUESTS; i++) {
            final int id = i;
            new Thread(() -> {
                try (Socket socket = new Socket(GATEWAY_HOST, GATEWAY_PORT);
                     BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                     PrintWriter writer = new PrintWriter(socket.getOutputStream(), true)) {

                    writer.print("GET / HTTP/1.1\r\n");
                    writer.print("Host: " + GATEWAY_HOST + ":" + GATEWAY_PORT + "\r\n");
                    writer.print("Connection: close\r\n\r\n");
                    writer.flush();

                    String statusLine = reader.readLine();
                    String serverName = "Unknown Server";
                    String line;
                    while ((line = reader.readLine()) != null && !line.isEmpty()) {
                        if (line.toLowerCase().startsWith("x-backend-server:")) {
                            serverName = line.substring("x-backend-server:".length()).trim();
                        }
                    }

                    stats.computeIfAbsent(serverName, k -> new AtomicInteger(0)).incrementAndGet();
                    System.out.printf("[Req #%02d] -> %s (%s)%n", id, serverName, statusLine);

                } catch (Exception e) {
                    System.err.printf("[Req #%02d] Lỗi: %s%n", id, e.getMessage());
                } finally {
                    latch.countDown();
                }
            }).start();
        }

        latch.await();
        long totalTime = System.currentTimeMillis() - startTime;

        System.out.println("\n=================================================");
        System.out.println("             KẾT QUẢ PHÂN PHỐI TẢI");
        System.out.println("=================================================");
        System.out.println("Tổng thời gian: " + totalTime + " ms");
        stats.forEach((server, count) -> {
            System.out.printf("- %-15s : %3d requests (%.1f%%)%n", 
                    server, count.get(), (count.get() * 100.0 / TOTAL_REQUESTS));
        });
        System.out.println("=================================================");
    }
}