package Gateway;

import java.net.Socket;
import java.net.ServerSocket;

public class Gateway {
    private static final int PORT = 5000;

    // hàm nhận request từ client
    public void acceptClientConnection() {

    }

    // hàm kết nối với server
    public void connectToServer() {
        try {
            // tạo kết nối đến server
            Socket socket = new Socket("localhost", PORT);

            System.out.print("Kết nối thành công");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] a) {
        Gateway gw = new Gateway();
        gw.connectToServer();

    }
}
