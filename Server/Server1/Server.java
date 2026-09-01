package Server.Server1;

import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import oshi.SystemInfo;
import oshi.hardware.GlobalMemory;

public class Server {
    private static final int Port = 5000;
    private static final int PortMonitor = 5001;
    ExecutorService threadPool = Executors.newFixedThreadPool(20);
    ServerSocket serverSocket = null;

    public void connection(SystemMonitor monitor) {
        try {

            // tạo sever lắng nghe

            this.serverSocket = new ServerSocket(Port);

            while (true) {

                Socket clientSocket = serverSocket.accept();
                PrintWriter writer = new PrintWriter(
                        clientSocket.getOutputStream(), true);

                threadPool.submit(() -> {
                    while (true) {
                        try {
                            String message;
                            message = monitor.printCurrentUsage();
                            writer.println("Server 1 : " + message);
                            System.out.println(message);
                            Thread.sleep(10000);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }

                    }

                });

            }
        }

        catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        SystemMonitor monitor = new SystemMonitor();
        Server sv = new Server();
        sv.connection(monitor);

    }
}
