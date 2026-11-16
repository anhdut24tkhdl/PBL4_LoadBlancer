Hướng dẫn chạy và kiểm thử thuật toán Round Robin và Least Connection
B1: Bien dich toan bo pj: 
    + javac -d bin -cp . (Get-ChildItem -Recurse -Filter *.java | ForEach-Object { $_.FullName })
B2: Khoi dong 3 server:
    + java -cp bin Server.BackendServerApp "Server 1" 5001
    + java -cp bin Server.BackendServerApp "Server 2" 5002
    + java -cp bin Server.BackendServerApp "Server 3" 5003
B3: Chạy và test từng thuật toán
    - Round Robin
        + Mở terminal mới, khởi động Gatewat với thuật toán Round Robin:
            java -cp bin Gateway.GatewayServer round_robin
        + Mở một terminal khác và chạy test Round Robin:
            java -cp bin Client.AlgorithmTest round_robin
    - Least Connection
        + Mở terminal mới, khởi động Gateway với thuật toán Least Connection:
            java -cp bin Gateway.GatewayServer least_connection
        + Mở terminal khác và chạy test Least Connection:
            java -cp bin Client.AlgorithmTest least_connection
B4: Bắn đồng thời 100 request
    java -cp bin Client.ParallelStressTest
        