package Gateway.forwarding;

import Gateway.model.BackendServer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import java.io.OutputStream;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;

import Gateway.protocol.Request;
import java.util.logging.Level;
import java.util.logging.Logger;

public class RequestForwarder {
        private static final int CONNECT_TIMEOUT = 50000;
        private static final int READ_TIMEOUT = 50000;
        private static final Logger LOGGER = Logger.getLogger(RequestForwarder.class.getName());

        public void forward(
                        BackendServer backend,
                        Request request,
                        OutputStream clientOut) throws Exception {
                
                long startTime = System.nanoTime();

                try (Socket backendSocket = new Socket()) {

                        backendSocket.connect(
                                        new InetSocketAddress(
                                                        backend.getHost(),
                                                        backend.getPort()),
                                        CONNECT_TIMEOUT);

                        backendSocket.setSoTimeout(READ_TIMEOUT);

                        OutputStream backendOut = backendSocket.getOutputStream();
                        InputStream backendIn = backendSocket.getInputStream();

                        // Gửi request sang backend
                        request.writeTo(backendOut);
                        backendOut.flush();

                        // =========================
                        // BUFFER RESPONSE
                        // =========================

                        ByteArrayOutputStream responseBuffer = new ByteArrayOutputStream();

                        byte[] buffer = new byte[8192];

                        int n;

                        while ((n = backendIn.read(buffer)) != -1) {

                                responseBuffer.write(buffer, 0, n);
                        }

                        // Nếu chạy tới đây nghĩa là backend
                        // đã gửi xong response

                        // Lúc này MỚI gửi cho client
                        responseBuffer.writeTo(clientOut);
                        clientOut.flush();
                        long latencyMs = (System.nanoTime() - startTime) / 1_000_000;

                        LOGGER.info(() -> "event=REQUEST_COMPLETED"
                                + " backend=" + backend.getName()
                                + " latency_ms=" + latencyMs);
                }catch (SocketTimeoutException e) {
                        LOGGER.log(Level.WARNING,
                                "event=BACKEND_TIMEOUT backend=" + backend.getName(), e);
                        throw e;
                        } catch (IOException e) {
                        LOGGER.log(Level.WARNING,
                                "event=BACKEND_CONNECTION_ERROR backend=" + backend.getName(), e);
                        throw e;
                }
        }
}