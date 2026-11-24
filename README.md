“Kiểm thử HTTPS”
Cách test	            URL mở trên máy của thành viên	                Điều kiện
Tự chạy Gateway local	https://localhost:5000/OnlineExam/	        Có JDK 21, Tomcat theo config.properties, keystore .p12 của chính họ và hai biến        
                                                                    GATEWAY_TLS_KEYSTORE, GATEWAY_TLS_PASSWORD.
Test bản Azure qua SSH	https://localhost:15443/OnlineExam/	        Gateway trên VM đang nghe ở 5443, có SSH tunnel 15443 → 5443 và máy client tin cậy CA đã 
                                                                    ký chứng chỉ của VM.