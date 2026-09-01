package Client;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Scanner;

public class Client {
    private static final String GATEWAY_HOST = "localhost";
    private static final int GATEWAY_PORT = 5000;

    public void connectToGateway() {
        try {
            Socket socket = new Socket(GATEWAY_HOST, GATEWAY_PORT);

            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(
                            socket.getInputStream()));

            PrintWriter writer = new PrintWriter(
                    socket.getOutputStream(),
                    true);

            while (true) {
                String response = reader.readLine();

                System.out.println(
                        "Response: " + response);
            }
        }

        catch (Exception e) {
            System.out.println(
                    "Không thể kết nối tới Gateway");
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        Thread th1 = new Thread(
                () -> {
                    Client client = new Client();
                    client.connectToGateway();

                });
        th1.start();
    }
}