
package Server;

import java.net.ServerSocket;
import java.net.Socket;

/**
 *
 * @author PC
 */
public class Server {
    private static final int PORT = 5000;
    private double cpuUsage;
    private double ramUsage;

    ServerSocket serverSocket = null;

    public void connection() {
        try {

            // tạo sever lắng nghe

            this.serverSocket = new ServerSocket(PORT);

            // chấp nhận kết nối từ client

            Socket clientSocket = serverSocket.accept();

             // Thread.sleep(5000);

            // đóng kết nối phía client
                clientSocket.close();

            // đóng server
             // serverSocket.close();
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    public static void main(String[] a) {
        Server sv = new Server();
        sv.connection();
    }

}
