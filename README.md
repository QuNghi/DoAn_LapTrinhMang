# DoAn_LapTrinhMang - Game Đặt Bom Multiplayer (UDP Socket)

Đồ án môn Lập Trình Mạng: Xây dựng trò chơi Bomberman / Bomb It nhiều người chơi thời gian thực qua giao thức mạng UDP theo mô hình **Authoritative Server**.

---

## 👥 Phân Công Thành Viên Trong Nhóm
* **Thành viên 1 (Bạn - The Network & Server Lead):** Chịu trách nhiệm thiết kế và lập trình Server Backend, DatagramSocket UDP, Game Loop 30 FPS, thuật toán va chạm (Collision Detection) và tính toán bom nổ.
* **Thành viên 2 (The UI & Input Lead):** Chịu trách nhiệm giao diện Client (JavaFX / Swing), Game Canvas vẽ bản đồ, nhân vật, bắt sự kiện phím (WASD, Space) gửi lên Server.
* **Thành viên 3 (The State Sync & Integration Lead):** Chịu trách nhiệm cầu nối Client-Server, giải mã gói tin (Packet Parser), đồng bộ vị trí mượt mà, màn hình Win/Lose và kiểm thử mạng LAN.

---

## 📁 Cấu Trúc Thư Mục Dự Án

```
DoAn_LapTrinhMang/
├── CONG_NGHE_DONG_BO.txt        # Tài liệu đặc tả giao thức và chuẩn công nghệ Client-Server
├── planttext_bomberman.puml      # Sơ đồ PlantText/PlantUML (Kiến trúc, Sequence, Class diagram)
├── compile.bat                   # File thực thi 1-click biên dịch Java
├── run_server.bat                # File thực thi 1-click khởi chạy Server
├── run_test_client.bat           # File thực thi 1-click chạy Test Client (mô phỏng)
├── Ke Hoach.docx                # Kế hoạch chi tiết đồ án
├── src/
│   └── com/bomberman/
│       ├── common/               # Các lớp dùng chung giữa Server và Client
│       │   ├── GameConstants.java # Hằng số (Port: 9876, Tick: 30 FPS, Map: 15x13, Bom: 3s)
│       │   ├── Direction.java     # Hướng di chuyển: UP, DOWN, LEFT, RIGHT, STOP
│       │   └── TileType.java      # Loại ô: EMPTY(0), HARD_WALL(1), SOFT_BRICK(2), BOMB(3)
│       ├── server/               # Mã nguồn Server (Nhiệm vụ Thành viên 1)
│       │   ├── model/
│       │   │   ├── Player.java    # Quản lý trạng thái, tọa độ và điểm số người chơi
│       │   │   ├── Bomb.java      # Quản lý quả bom, đếm ngược nổ
│       │   │   └── GameMap.java   # Quản lý ma trận bản đồ 15x13
│       │   ├── network/
│       │   │   ├── PlayerSession.java # Quản lý phiên kết nối (IP, Port, Heartbeat)
│       │   │   └── UdpServer.java     # Lớp DatagramSocket nhận/gửi UDP bất đồng bộ
│       │   ├── logic/
│       │   │   ├── CollisionDetector.java # Kiểm tra va chạm tường cứng, gạch mềm, bom
│       │   │   ├── ExplosionHandler.java  # Tính toán tia lửa 4 hướng, phá gạch, tiêu diệt player
│       │   │   └── GameEngine.java        # Vòng lặp Authoritative Game Loop (30 Ticks/giây)
│       │   └── ServerMain.java   # Điểm khởi chạy Server console
│       └── client/
│           └── TestClient.java   # Client dòng lệnh độc lập để kiểm thử và làm mẫu cho TV2 & TV3
```

---

## 🚀 Hướng Dẫn Biên Dịch & Chạy Thử Nghiệm

### Cách 1: Sử dụng file script `.bat` (Dành cho Windows)
1. Double-click `compile.bat` để biên dịch toàn bộ mã nguồn.
2. Double-click `run_server.bat` để khởi động Server tại cổng `9876`.
3. Double-click `run_test_client.bat` để mở client kiểm thử. Nhập tên và dùng các phím `w, a, s, d` để di chuyển, `b` để đặt bom.

### Cách 2: Sử dụng lệnh trong PowerShell / Terminal
```powershell
# 1. Biên dịch
javac -encoding UTF-8 -d bin src/com/bomberman/common/*.java src/com/bomberman/server/model/*.java src/com/bomberman/server/network/*.java src/com/bomberman/server/logic/*.java src/com/bomberman/server/*.java src/com/bomberman/client/*.java

# 2. Chạy Server
java -cp bin com.bomberman.server.ServerMain 9876

# 3. Chạy Client kiểm thử (mở terminal mới)
java -cp bin com.bomberman.client.TestClient Player1 127.0.0.1 9876
```

---

## 📡 Chuẩn Gói Tin Mạng (Xem chi tiết tại CONG_NGHE_DONG_BO.txt)
* **Client -> Server:**
  - `JOIN|<tên>` : Gia nhập phòng.
  - `MOVE|<UP|DOWN|LEFT|RIGHT|STOP>` : Di chuyển.
  - `BOMB` : Đặt bom tại tọa độ hiện tại.
  - `PING` : Giữ kết nối (heartbeat).
  - `QUIT` : Thoát game.
* **Server -> Client:**
  - `JOIN_ACK|<id>|<status>|<message>` : Xác nhận gia nhập.
  - `MAP_INIT|<width>|<height>|<matrix>` : Dữ liệu bản đồ.
  - `STATE|<tick>|<players_data>|<bombs_data>` : Đồng bộ tọa độ thế giới (30 FPS).
  - `EXPLODE|<bombId>|<danh_sách_ô_nổ>` : Bom nổ hiệu ứng lửa.
  - `TILE_UPDATE|<x>|<y>|<tile>` : Cập nhật ô gạch vỡ thành ô trống.
  - `PLAYER_DIED|<id>|<killerId>` : Người chơi bị hạ gục.
  - `GAME_OVER|<winnerId>|<winnerName>` : Kết thúc ván chơi.