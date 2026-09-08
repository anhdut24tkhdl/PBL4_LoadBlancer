package Server;

public class BackendServerApp {
    public static void main(String[] args) {
        String name = args.length > 0 ? args[0] : "Server-1";
        int port = args.length > 1 ? Integer.parseInt(args[1]) : 5001;
        
        SystemMonitor monitor = new SystemMonitor();
        new GenericServer(name, port, monitor).start();
    }
}