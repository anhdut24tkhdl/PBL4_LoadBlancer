package Gateway;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.Socket;

public class Gateway {
    // private static final int PORT = 5000;

    public void connectToServer(int Port) {
        try (
                Socket socket = new Socket("localhost", Port);
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(socket.getInputStream()))) {
            System.out.println("Đã kết nối tới Server");

            String response;

            while ((response = reader.readLine()) != null) {
                System.out.println("Server: " + response);
            }

            System.out.println("Server đã ngắt kết nối");

        } catch (Exception e) {
            // Phải in lỗi để biết kết nối thất bại ở đâu
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        Gateway gateway = new Gateway();
        Gateway gateway1 = new Gateway();
        Thread th = new Thread(
                () -> gateway.connectToServer(5000)

        );
        Thread th1 = new Thread(
                () -> gateway1.connectToServer(5002)

        );

        th.start();
        th1.start();

    }
}