package Gateway.handler;

import Gateway.forwarding.RequestForwarder;
import Gateway.loadbalancer.LoadBalancer;
import Gateway.model.BackendServer;
import Gateway.protocol.Request;
import java.io.InputStream;
import java.io.OutputStream;
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

            for (int i = 0; i <= maxRetries; i++) {
                BackendServer backend = loadBalancer.selectServer();
                if (backend == null)
                    break;

                try {

                    backend.increaseConnections();

                    // Chỉ khi forward() hoàn thành
                    // thì client mới nhận response
                    forwarder.forward(
                            backend,
                            request,
                            clientOut);

                    // Thành công
                    break;

                } catch (Exception e) {

                    // Backend này có vấn đề
                    backend.setAlive(false);

                    System.err.println(
                            "Backend " + backend.getName()
                                    + " lỗi, retry...");

                } finally {

                    backend.decreaseConnections();
                }
            }

        } catch (

        Exception e) {
            System.out.println(
                    "Lỗi xử lý client: " + e.getMessage());
        }
    }
}