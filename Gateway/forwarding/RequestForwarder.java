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
import Gateway.utils.AppLogger;

public class RequestForwarder {
        private static final int CONNECT_TIMEOUT = 3000;
        private static final int READ_TIMEOUT = 5000;

        public void forward(BackendServer backend, Request request, OutputStream clientOut) throws Exception {
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

                        // Gửi response về client sau khi đã đọc xong từ backend.
                        try {
                                responseBuffer.writeTo(clientOut);
                                clientOut.flush();
                        } catch (IOException e) {
                                // Ghi log lỗi khi không thể gửi response về client
                                AppLogger.LOGGER.log(java.util.logging.Level.WARNING,
                                        "event=CLIENT_WRITE_ERROR backend=" + backend.getName(), e);
                                throw new ClientWriteException("Cannot write response to client", e);
                        }

                        long latencyMs = (System.nanoTime() - startTime) / 1_000_000;


                        // Ghi log thông tin khi hoàn thành request
                        AppLogger.LOGGER.info("event=REQUEST_COMPLETED" + " backend=" + backend.getName()
                                + " latency_ms=" + latencyMs);

                }catch (SocketTimeoutException e) {
                        // Ghi log cảnh báo khi backend timeout
                        AppLogger.LOGGER.log(Level.WARNING,
                                "event=BACKEND_TIMEOUT backend=" + backend.getName(), e);
                        throw e;

                } catch (IOException e) {
                        // Ghi log cảnh báo khi có lỗi kết nối với backend
                        AppLogger.LOGGER.log(Level.WARNING,
                                "event=BACKEND_CONNECTION_ERROR backend=" + backend.getName(), e);
                        throw e;
                }
        }
        public static class ClientWriteException extends IOException {
                public ClientWriteException(String message, IOException cause) {
                super(message, cause);
                }
        }
}