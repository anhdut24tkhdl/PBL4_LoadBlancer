package Gateway.handler;

import Gateway.forwarding.RequestForwarder;
import Gateway.loadbalancer.LoadBalancer;
import Gateway.model.BackendServer;
import Gateway.protocol.Response;
import Gateway.protocol.Request;


import java.io.BufferedReader;
import java.io.InputStreamReader;
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

            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(this.clientSocket.getInputStream()));
            PrintWriter writer = new PrintWriter(
                    this.clientSocket.getOutputStream(), true)) {
            Request req=Request.parse(reader.readLine());

            if (req == null) {
                return;
            }

            int maxRetries = 2;
            String response = null;
            for (int i = 0; i <= maxRetries; i++) {
                BackendServer backend = loadBalancer.selectServer();
                if (backend == null) break;
                
                try {
                    backend.increaseConnections();
                    response = forwarder.forward(backend, req.serialize());
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