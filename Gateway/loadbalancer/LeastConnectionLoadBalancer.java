// package Gateway.loadbalancer;

// import Gateway.model.BackendServer;
// import Gateway.registry.ServerRegistry;

// import java.util.ArrayList;
// import java.util.List;
// import java.util.stream.Collectors;

// /**
//  * Thuật toán Least Connection:
//  * Chuyển request đến server đang có ít kết nối hoạt động nhất (active connections thấp nhất).
//  */
// public class LeastConnectionLoadBalancer implements LoadBalancer {
//     private final ServerRegistry registry;

//     // Vị trí tiếp theo khi cần phân phối Round Robin
//     private int nextIndex = 0;

//     public LeastConnectionLoadBalancer(ServerRegistry registry) {
//         this.registry = registry;
//     }

//     /**
//      * Chọn backend có ít kết nối nhất.
//      * Nếu hòa, luân phiên giữa các backend cùng mức tải thấp nhất.
//      */
//     private BackendServer findLeastConnectedServer() {
//         List<BackendServer> aliveServers = new ArrayList<>();

//         for (BackendServer server : registry.getServers()) {
//             if (server.isAlive()) {
//                 aliveServers.add(server);
//             }
//         }

//         if (aliveServers.isEmpty()) {
//             return null;
//         }

//         // Tìm số kết nối nhỏ nhất
//         int minConnections = Integer.MAX_VALUE;

//         for (BackendServer server : aliveServers) {
//             minConnections = Math.min(
//                     minConnections,
//                     server.getActiveConnections()
//             );
//         }

//         // Lấy tất cả backend có số kết nối bằng mức nhỏ nhất
//         List<BackendServer> candidates = new ArrayList<>();

//         for (BackendServer server : aliveServers) {
//             if (server.getActiveConnections() == minConnections) {
//                 candidates.add(server);
//             }
//         }

//         // Round Robin giữa các backend đang hòa
//         int selectedIndex = Math.floorMod(
//                 nextIndex++,
//                 candidates.size()
//         );

//         return candidates.get(selectedIndex);
//     }

//     @Override
//     public BackendServer selectServer() {
//         List<BackendServer> aliveServers = registry.getServers().stream()
//                 .filter(BackendServer::isAlive)
//                 .collect(Collectors.toList());

//         if (aliveServers.isEmpty()) {
//             return null;
//         }

//         // Tìm server có active connections nhỏ nhất
//         BackendServer bestServer = aliveServers.get(0);
//         int minConnections = bestServer.getActiveConnections();

//         for (int i = 1; i < aliveServers.size(); i++) {
//             BackendServer current = aliveServers.get(i);
//             int currentConn = current.getActiveConnections();
//             if (currentConn < minConnections) {
//                 minConnections = currentConn;
//                 bestServer = current;
//             }
//         }

//         return bestServer;
//     }

//     @Override
//     public synchronized BackendServer selectServerForRequest() {
//         BackendServer server = findLeastConnectedServer();

//         if (server != null) {
//             server.increaseConnections();
//         }

//         return server;
//     }
// }

package Gateway.loadbalancer;

import Gateway.model.BackendServer;
import Gateway.registry.ServerRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class LeastConnectionLoadBalancer implements LoadBalancer {

    private final ServerRegistry registry;

    // Dùng để phá hòa khi các server có cùng số kết nối.
    private final AtomicInteger tieBreaker = new AtomicInteger(0);

    public LeastConnectionLoadBalancer(ServerRegistry registry) {
        this.registry = registry;
    }

    @Override
    public BackendServer selectServer() {

        List<BackendServer> aliveServers = registry.getServers()
                .stream()
                .filter(BackendServer::isAlive)
                .collect(Collectors.toList());

        if (aliveServers.isEmpty()) {
            return null;
        }

        int minConnections = aliveServers.stream()
                .mapToInt(BackendServer::getActiveConnections)
                .min()
                .orElse(Integer.MAX_VALUE);

        // Lấy tất cả server có số kết nối thấp nhất.
        List<BackendServer> candidates = new ArrayList<>();

        for (BackendServer server : aliveServers) {
            if (server.getActiveConnections() == minConnections) {
                candidates.add(server);
            }
        }

        // Luân phiên giữa những server đang ít kết nối nhất.
        int index = Math.floorMod(
                tieBreaker.getAndIncrement(),
                candidates.size()
        );

        return candidates.get(index);
    }
}