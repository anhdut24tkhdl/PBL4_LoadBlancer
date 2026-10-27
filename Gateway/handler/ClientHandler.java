package Gateway.handler;

import Gateway.forwarding.RequestForwarder;
import Gateway.loadbalancer.LoadBalancer;
import Gateway.model.BackendServer;
import Gateway.protocol.Request;
import Gateway.utils.AppLogger;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class ClientHandler implements Runnable {
    private final Socket clientSocket;
    private final LoadBalancer loadBalancer;
    private final RequestForwarder forwarder;

    public ClientHandler(Socket clientSocket, LoadBalancer loadBalancer, RequestForwarder forwarder) {
        this.clientSocket = clientSocket;
        this.loadBalancer = loadBalancer;
        this.forwarder = forwarder;
    }

    @Override
    public void run() {
        try (
<<<<<<< HEAD
            Socket client = clientSocket;
            InputStream clientIn = client.getInputStream();
            OutputStream clientOut = client.getOutputStream()) {

=======

                Socket client = clientSocket;
                InputStream clientIn = client.getInputStream();
                OutputStream clientOut = client.getOutputStream()) {
>>>>>>> origin/develop
            // Không dùng readAllBytes()
            Request request = Request.readRequest(client);
            int maxRetries = 2;
<<<<<<< HEAD
            boolean completed = false;
            boolean clientWriteFailed = false;
=======
>>>>>>> origin/develop

            for (int i = 0; i <= maxRetries; i++) {
                BackendServer backend = loadBalancer.selectServer();
                if (backend == null){
                    AppLogger.LOGGER.warning("event=NO_BACKEND_AVAILABLE attempt=" + (i + 1));
                    break;
                }

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

                } catch (RequestForwarder.ClientWriteException e) {
                    // The client disconnected or rejected the response; do not mark backend DOWN or retry.
                    AppLogger.LOGGER.info("event=CLIENT_DISCONNECTED backend=" + backend.getName());
                    clientWriteFailed = true;
                    break;
                } catch (IOException e) {
                    backend.setAlive(false);
<<<<<<< HEAD
                    AppLogger.LOGGER.log(java.util.logging.Level.WARNING,
                            "event=BACKEND_RETRY backend=" + backend.getName()
                                    + " attempt=" + (i + 1)
                                    + " max_attempts=" + (maxRetries + 1), e);
=======

                    System.err.println(
                            "Backend " + backend.getName()
                                    + " lỗi, retry...");

>>>>>>> origin/develop
                } finally {

                    backend.decreaseConnections();
                }
            }
<<<<<<< HEAD
        
            if (!completed && !clientWriteFailed && !client.isClosed()) {
                sendServiceUnavailable(clientOut);
                // Ghi log cảnh báo khi request không thành công
                AppLogger.LOGGER.warning("event=REQUEST_FAILED status=503");
            }

        } catch (Exception e) {
            // Ghi log lỗi khi có exception xảy ra
            System.out.println("Lỗi xử lý client: " + e.getMessage());
            AppLogger.LOGGER.log(java.util.logging.Level.WARNING, "event=CLIENT_HANDLER_ERROR", e);
        }
    }


    private void sendServiceUnavailable(OutputStream out) throws java.io.IOException {
        byte[] body = "Service Unavailable".getBytes(
                java.nio.charset.StandardCharsets.UTF_8);

        String headers = "HTTP/1.1 503 Service Unavailable\r\n"
                + "Content-Type: text/plain; charset=UTF-8\r\n"
                + "Content-Length: " + body.length + "\r\n"
                + "Connection: close\r\n\r\n";

        try {
            out.write(headers.getBytes(StandardCharsets.US_ASCII));
            out.write(body);
            out.flush();
        } catch (IOException e) {
            AppLogger.LOGGER.fine("event=CLIENT_503_WRITE_FAILED message=" + e.getMessage());
        }
    }
=======

        } catch (

        Exception e) {
            System.out.println(
                    "Lỗi xử lý client: " + e.getMessage());
        }
    }
>>>>>>> origin/develop
}