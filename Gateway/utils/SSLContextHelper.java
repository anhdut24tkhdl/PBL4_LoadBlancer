package Gateway.utils;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocketFactory;
import java.io.FileInputStream;
import java.io.InputStream;
import java.security.KeyStore;

public class SSLContextHelper {

    /**
     * Tạo SSLServerSocketFactory từ file Keystore PKCS12.
     * 
     * @param keystorePath Đường dẫn file .p12 (ví dụ "gateway-keystore.p12")
     * @param password Mật khẩu dùng để mở keystore PKCS12
     * @return SSLServerSocketFactory để tạo SSLServerSocket
     */
    public static SSLServerSocketFactory createSSLServerSocketFactory(String keystorePath, String password) throws Exception {
        // 1. Khởi tạo đối tượng KeyStore kiểu PKCS12
        KeyStore keyStore = KeyStore.getInstance("PKCS12");

        // 2. Nạp file keystore từ ổ đĩa
        try (InputStream in = new FileInputStream(keystorePath)) {
            keyStore.load(in, password.toCharArray());
        }

        // 3. Khởi tạo KeyManagerFactory để quản lý private key và certificate
        KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keyManagerFactory.init(keyStore, password.toCharArray());

        // 4. Khởi tạo SSLContext hỗ trợ giao thức TLS hiện đại
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(keyManagerFactory.getKeyManagers(), null, null);

        return sslContext.getServerSocketFactory();
    }
}