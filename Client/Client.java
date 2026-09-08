package Client;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Scanner;

import Gateway.protocol.Request;
import Gateway.protocol.Response;

public class Client {
    private static final String GATEWAY_HOST = "localhost";
    private static final int GATEWAY_PORT = 5000;

    public void connectToGateway() {
        try (
                Socket socket = new Socket(
                        GATEWAY_HOST,
                        GATEWAY_PORT);

                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(
                                socket.getInputStream()));

                PrintWriter writer = new PrintWriter(
                        socket.getOutputStream(),
                        true);)

        {

            // String request = "PING";

            // writer.println(request);
            Request req=new Request("PING", "Client test load banlance");
            writer.println(req.serialize());

            Response res = Response.parse(reader.readLine());

            if (res == null) {
                System.out.println("[Client] Gateway đã đóng kết nối mà không phản hồi.");
                return;
            }

            System.out.println("[Client] Nhận phản hồi từ " + res.getServerName() 
                    + " [Mã " + res.getStatusCode() + "]: " + res.getMessage());

        } catch (Exception e) {
            System.out.println("Không thể kết nối Gateway");
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        Thread th = new Thread(() -> new Client().connectToGateway());
        th.start();
    }
}