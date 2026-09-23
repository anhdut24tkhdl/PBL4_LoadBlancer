package Gateway.handler;

import Gateway.forwarding.RequestForwarder;
import Gateway.loadbalancer.LoadBalancer;
import Gateway.model.BackendServer;
import Gateway.protocol.Response;
import Gateway.protocol.Request;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.Socket;

public class ClientHandler implements Runnable {
    private final Socket clientSocket;
    private final LoadBalancer loadBalancer;
    private final RequestForwarder forwarder;

    public ClientHandler(
            Socket clientSocket,
            LoadBalancer loadBalancer,
            RequestForwarder forwarder) {
        this.clientSocket = clientSocket;
        this.loadBalancer = loadBalancer;
        this.forwarder = forwarder;
    }

    @Override
    public void run() {
        try (

                Socket client = clientSocket;
                InputStream clientIn = client.getInputStream();
                OutputStream clientOut = client.getOutputStream()) {
            // Không dùng readAllBytes()
            Request request = Request.readRequest(client);

            int maxRetries = 2;
            String response = null;
            for (int i = 0; i <= maxRetries; i++) {
                BackendServer backend = loadBalancer.selectServer();
                if (backend == null)
                    break;

                try {
                    backend.increaseConnections();
                    forwarder.forward(backend, request, clientOut);
                    break; // Thành công
                } catch (Exception e) {
                    backend.setAlive(false); // Đánh dấu server lỗi ngay lập tức
                    System.err.println("Lỗi forward đến " + backend.getName() + ", đang thử server khác...");
                } finally {
                    backend.decreaseConnections();
                }
            }

        } catch (Exception e) {
            System.out.println(
                    "Lỗi xử lý client: " + e.getMessage());
        }
    }
}