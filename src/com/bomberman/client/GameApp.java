package com.bomberman.client;

import com.bomberman.common.GameConstants;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;

/**
 * Cửa sổ chính của ứng dụng Client (GameApp).
 * Quản lý chuyển đổi màn hình giữa: Sảnh chính (Lobby), Phòng chờ (Room Lobby), và Trận đấu (Game).
 */
public class GameApp extends JFrame {

    private final ClientGameState state = new ClientGameState();
    private GameClient client;

    private final CardLayout cardLayout = new CardLayout();
    private final JPanel rootPanel = new JPanel(cardLayout);

    private LobbyPanel lobbyPanel;
    private RoomPanel roomPanel;
    private GamePanel gamePanel;

    private static final String SCREEN_LOBBY = "LOBBY";
    private static final String SCREEN_ROOM  = "ROOM";
    private static final String SCREEN_GAME  = "GAME";

    public GameApp() {
        super("Bomb It Multiplayer - UDP");
        setupWindow();
        buildScreens();
        showLobby();
    }

    // ──────────────────────────────────────────────────────────
    //  Cấu hình cửa sổ
    // ──────────────────────────────────────────────────────────

    private void setupWindow() {
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setResizable(false);
        setBackground(new Color(0x0d1b2a));

        // Icon
        try {
            ImageIcon icon = new ImageIcon(
                getClass().getResource("/com/bomberman/client/res/icon.png"));
            setIconImage(icon.getImage());
        } catch (Exception ignored) {
            // Không có file icon thì dùng mặc định
        }

        add(rootPanel);
    }

    // ──────────────────────────────────────────────────────────
    //  Xây dựng các màn hình
    // ──────────────────────────────────────────────────────────

    private void buildScreens() {
        // 1. Lobby Panel
        lobbyPanel = new LobbyPanel(new LobbyPanel.LobbyCallback() {
            @Override
            public void onQuickJoin(String name, String host, int port) {
                connectAndSend(name, host, port, () -> client.sendPacket("JOIN|" + name));
            }

            @Override
            public void onCreateRoom(String name, String host, int port) {
                connectAndSend(name, host, port, () -> client.sendCreateRoom(name));
            }

            @Override
            public void onJoinRoom(String roomCode, String name, String host, int port) {
                connectAndSend(name, host, port, () -> client.sendJoinRoom(roomCode, name));
            }
        });
        rootPanel.add(lobbyPanel, SCREEN_LOBBY);

        // 2. Room Panel (Phòng chờ)
        roomPanel = new RoomPanel(state, new RoomPanel.RoomActionCallback() {
            @Override
            public void onStartGame() {
                if (client != null) {
                    client.sendStartGame();
                }
            }

            @Override
            public void onLeaveRoom() {
                if (client != null) {
                    client.sendLeaveRoom();
                }
                showLobby();
            }

            @Override
            public void onKickPlayer(int playerId) {
                if (client != null) {
                    client.sendKickPlayer(playerId);
                }
            }

            @Override
            public void onSelectMap(int mapType) {
                if (client != null) {
                    client.sendSetMap(mapType);
                }
            }

            @Override
            public void onReadyToggle() {
                if (client != null) {
                    client.sendReadyToggle();
                }
            }
        });
        rootPanel.add(roomPanel, SCREEN_ROOM);

        // 3. Game panel
        gamePanel = new GamePanel(state, null, this);
        rootPanel.add(gamePanel, SCREEN_GAME);
    }

    // ──────────────────────────────────────────────────────────
    //  Kết nối Server
    // ──────────────────────────────────────────────────────────

    private void connectAndSend(String playerName, String host, int port, Runnable afterConnected) {
        if (client != null) {
            client.disconnect();
        }

        state.reset();
        client = new GameClient(host, port, state, this::handleServerEvent);

        new Thread(() -> {
            try {
                client.connect(playerName);

                // Chờ phản hồi JOIN_ACK (tối đa 5 giây)
                long timeout = System.currentTimeMillis() + 5000;
                while (!state.connected && System.currentTimeMillis() < timeout) {
                    Thread.sleep(50);
                }

                if (state.connected) {
                    afterConnected.run();
                    SwingUtilities.invokeLater(() -> {
                        showRoomLobby();
                        lobbyPanel.showStatus("✅ Đã kết nối!", new Color(0x4CAF50));
                    });
                } else {
                    SwingUtilities.invokeLater(() -> {
                        lobbyPanel.showStatus("❌ Không thể kết nối! Kiểm tra IP/Port.", new Color(0xFF4444));
                        lobbyPanel.setButtonsEnabled(true);
                        client.disconnect();
                    });
                }
            } catch (IOException e) {
                SwingUtilities.invokeLater(() -> {
                    lobbyPanel.showStatus("❌ Lỗi mạng: " + e.getMessage(), new Color(0xFF4444));
                    lobbyPanel.setButtonsEnabled(true);
                });
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "Connect-Thread").start();
    }

    // ──────────────────────────────────────────────────────────
    //  Callback xử lý sự kiện Server
    // ──────────────────────────────────────────────────────────

    private void handleServerEvent(String header, String[] parts) {
        SwingUtilities.invokeLater(() -> {
            switch (header) {
                case "ROOM_CREATED":
                case "ROOM_JOINED":
                case "RETURNED_TO_ROOM":
                    showRoomLobby();
                    break;

                case "COUNTDOWN":
                case "GAME_START":
                    // Chuyển ngay sang màn hình Game khi bắt đầu đếm ngược hoặc trận đấu
                    if (gamePanel == null || !gamePanel.isFocusOwner()) {
                        switchToGame();
                    }
                    break;

                case "QUIT_ACK":
                    if (parts.length > 1 && "KICKED".equals(parts[1])) {
                        JOptionPane.showMessageDialog(this, "Bạn đã bị chủ phòng kích khỏi phòng!", "Thông báo", JOptionPane.WARNING_MESSAGE);
                    } else if (parts.length > 1 && "ROOM_CLOSED".equals(parts[1])) {
                        JOptionPane.showMessageDialog(this, "Phòng đã bị quản trị viên máy chủ đóng!", "Thông báo", JOptionPane.INFORMATION_MESSAGE);
                    }
                    showLobby();
                    break;

                case "ERROR":
                    String err = parts.length > 1 ? parts[1] : "Có lỗi xảy ra!";
                    lobbyPanel.showStatus("⚠ " + err, new Color(0xFF6B35));
                    lobbyPanel.setButtonsEnabled(true);
                    break;

                case "RESTART_ACK":
                    if (parts.length > 1 && "ACCEPTED".equals(parts[1])) {
                        // Restart trận đấu
                        state.reset();
                        switchToGame();
                    } else if (parts.length > 1 && "REJECTED".equals(parts[1])) {
                        showLobby();
                    }
                    break;

                default:
                    break;
            }
        });
    }

    // ──────────────────────────────────────────────────────────
    //  Chuyển màn hình
    // ──────────────────────────────────────────────────────────

    public void showRoomLobby() {
        if (gamePanel != null) {
            gamePanel.stopRendering();
        }
        // Tăng chiều cao để đảm bảo nút BẮT ĐẦU NGAY không bị cắt
        setPreferredSize(new Dimension(600, 660));
        pack();
        setLocationRelativeTo(null);

        cardLayout.show(rootPanel, SCREEN_ROOM);
        roomPanel.start();
    }

    private void switchToGame() {
        if (roomPanel != null) {
            roomPanel.stop();
        }
        if (gamePanel != null) {
            gamePanel.stopRendering();
            rootPanel.remove(gamePanel);
        }
        gamePanel = new GamePanel(state, client, this);
        rootPanel.add(gamePanel, SCREEN_GAME);

        // Kích thước game: Bản đồ (15*48 = 720) + Sidebar (230) = 950px
        int mapW = GameConstants.MAP_WIDTH * 48 + 230;
        int mapH = GameConstants.MAP_HEIGHT * 48 + 40;
        setPreferredSize(new Dimension(mapW, mapH));
        pack();
        setLocationRelativeTo(null);

        cardLayout.show(rootPanel, SCREEN_GAME);
        gamePanel.startRendering();
        gamePanel.requestFocusInWindow();
    }

    public void showLobby() {
        if (roomPanel != null) {
            roomPanel.stop();
        }
        if (gamePanel != null) {
            gamePanel.stopRendering();
        }
        if (client != null) {
            client.disconnect();
            client = null;
        }
        state.reset();

        setPreferredSize(new Dimension(500, 620));
        pack();
        setLocationRelativeTo(null);

        lobbyPanel.setButtonsEnabled(true);
        lobbyPanel.showStatus(" ", Color.WHITE);
        cardLayout.show(rootPanel, SCREEN_LOBBY);
    }

    // ──────────────────────────────────────────────────────────
    //  Entry Point
    // ──────────────────────────────────────────────────────────

    public static void main(String[] args) {
        try {
            // Sử dụng Metal L&F để đảm bảo setForeground/setBackground được tôn trọng
            UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
        } catch (Exception ignored) {}

        System.setProperty("awt.useSystemAAFontSettings", "on");
        System.setProperty("swing.aatext", "true");

        SwingUtilities.invokeLater(() -> {
            GameApp app = new GameApp();
            app.setVisible(true);
        });
    }
}
