package Gateway.handler;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Random;

public class ClientHandler extends Thread {
    private Socket clientSocket;

    public ClientHandler(Socket clientSocket)

    {
        this.clientSocket = clientSocket;

    }

    @Override
    public void run() {

        try {
            Random ramdom = new Random();
            int port = ramdom.nextBoolean() ? 5001 : 5002;

            String message;
            Socket socket = new Socket("localhost", port);

            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(socket.getInputStream()));
            PrintWriter writer = new PrintWriter(

                    clientSocket.getOutputStream(), true);

            while (true) {
                while ((message = reader.readLine()) != null) {
                    System.out.println(message);
                    writer.println(message);
                }

            }

        } catch (Exception e) {
            // TODO: handle exception
        }

    }

}