package Gateway;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import Gateway.handler.ClientHandler;

public class GatewayServer {
    // private static final int PORT = 5000;
    ExecutorService threadPool = Executors.newFixedThreadPool(20);
    ScheduledExecutorService monitorPool = Executors.newScheduledThreadPool(2);

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

    public void test(ClientHandler client) {
        threadPool.execute(client);

    }

    public static void main(String[] args) {
        try {
            GatewayServer gw = new GatewayServer();
            ServerSocket server = new ServerSocket(6000);
            System.out.print("tao in o day");

            while (true) {
                Socket client = server.accept();
                System.out.print("tao in o day");
                gw.test(new ClientHandler(client));
            }

        } catch (Exception e) {
            // TODO: handle exception
        }

    }
}