package Gateway.handler;

import Gateway.forwarding.RequestForwarder;
import Gateway.loadbalancer.LoadBalancer;
import Gateway.model.BackendServer;

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
            String request = reader.readLine();

            if (request == null) {
                return;
            }

            BackendServer backend = loadBalancer.selectServer();

            if (backend == null) {
                writer.println("ERROR: Không có server khả dụng");
                return;
            }

            backend.increaseConnections();

            try {

                String response = forwarder.forward(backend, request);

                writer.println(response);

            } finally {
                backend.decreaseConnections();
            }

        } catch (Exception e) {
            System.out.println(
                    "Lỗi xử lý client: " + e.getMessage());
        }
    }
}