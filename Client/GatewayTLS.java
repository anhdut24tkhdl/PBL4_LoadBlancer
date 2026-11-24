package Client;

import java.io.IOException;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

public final class GatewayTLS {

    private GatewayTLS() {
    }

    public static SSLSocket connect(String host, int port) throws IOException {
        SSLSocketFactory factory =
                (SSLSocketFactory) SSLSocketFactory.getDefault();

        SSLSocket socket = (SSLSocket) factory.createSocket(host, port);

        try {
            SSLParameters parameters = socket.getSSLParameters();
            parameters.setEndpointIdentificationAlgorithm("HTTPS");
            socket.setSSLParameters(parameters);

            socket.setSoTimeout(15_000);
            socket.startHandshake();
            return socket;
        } catch (IOException | RuntimeException e) {
            try {
                socket.close();
            } catch (IOException closeError) {
                e.addSuppressed(closeError);
            }
            throw e;
        }
    }
}