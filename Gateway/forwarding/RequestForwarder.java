package Gateway.forwarding;

import Gateway.model.BackendServer;

import java.io.InputStream;

import java.io.OutputStream;

import java.net.InetSocketAddress;
import java.net.Socket;
import Gateway.protocol.Request;

public class RequestForwarder {
        private static final int CONNECT_TIMEOUT = 3000;
        private static final int READ_TIMEOUT = 5000;

        public void forward(
                        BackendServer backend,
                        Request request,
                        OutputStream clientOut) throws Exception {

                try (Socket backendSocket = new Socket()) {
                        backendSocket.connect(
                                        new InetSocketAddress(backend.getHost(), backend.getPort()),
                                        CONNECT_TIMEOUT);

                        backendSocket.setSoTimeout(READ_TIMEOUT);

                        OutputStream backendOut = backendSocket.getOutputStream();
                        InputStream backendIn = backendSocket.getInputStream();

                        // Gửi header + body request xuống backend
                        request.writeTo(backendOut);

                        // Bản đơn giản: backend phải trả Connection: close
                        byte[] buffer = new byte[8192];
                        int n;

                        while ((n = backendIn.read(buffer)) != -1) {
                                clientOut.write(buffer, 0, n);
                        }

                        clientOut.flush();
                }
        }
}