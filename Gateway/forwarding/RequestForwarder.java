package Gateway.forwarding;

import Gateway.model.BackendServer;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;

public class RequestForwarder {
        private static final int CONNECT_TIMEOUT = 3000;
        private static final int READ_TIMEOUT = 5000;

        public String forward(BackendServer backend, String request) throws Exception {
                try (Socket backendSocket = new Socket()) {
                        backendSocket.connect(
                                        new InetSocketAddress(
                                                        backend.getHost(),
                                                        backend.getPort()),
                                        CONNECT_TIMEOUT);

                        backendSocket.setSoTimeout(READ_TIMEOUT);

                        try (
                                        BufferedReader reader = new BufferedReader(
                                                        new InputStreamReader(
                                                                        backendSocket.getInputStream()));
                                        PrintWriter writer = new PrintWriter(
                                                        backendSocket.getOutputStream(), true)) {
                                writer.println(request);

                                String response = reader.readLine();

                                if (response == null) {
                                        throw new Exception(
                                                        "Backend không trả response");
                                }

                                return response;
                        }
                }
        }
}