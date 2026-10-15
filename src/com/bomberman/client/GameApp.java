package com.bomberman.client;

import com.bomberman.common.GameConstants;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;

/**
 * Cửa sổ chính của ứng dụng Client (GameApp).
 * Quản lý chuyển màn hình giữa Lobby và Game.
 */
public class GameApp extends JFrame {

    private final ClientGameState state = new ClientGameState();
    private GameClient client;

    private final CardLayout cardLayout = new CardLayout();
    private final JPanel rootPanel = new JPanel(cardLayout);

    private LobbyPanel lobbyPanel;
    private GamePanel gamePanel;

    private static final String SCREEN_LOBBY = "LOBBY";
    private static final String SCREEN_GAME  = "GAME";

    public GameApp() {
        super("💣 Bomb It Multiplayer – UDP");
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
            // Không có file icon thì dùng default
        }

        add(rootPanel);
    }

    // ──────────────────────────────────────────────────────────
    //  Xây dựng các màn hình
    // ──────────────────────────────────────────────────────────

    private void buildScreens() {
        // Lobby
        lobbyPanel = new LobbyPanel(this::handleConnectRequest);
        rootPanel.add(lobbyPanel, SCREEN_LOBBY);

        // Game panel (tạo tạm, sẽ build lại khi connect)
        gamePanel = new GamePanel(state, null, this);
        rootPanel.add(gamePanel, SCREEN_GAME);
    }

    // ──────────────────────────────────────────────────────────
    //  Xử lý khi người dùng nhấn Kết Nối
    // ──────────────────────────────────────────────────────────

    private void handleConnectRequest(String playerName, String host, int port) {
        // Nếu có client cũ, ngắt trước
        if (client != null) {
            client.disconnect();
        }

        // Reset state - không set tên trước, chờ JOIN_ACK để biết ID thật
        state.reset();

        // Tạo client mới
        client = new GameClient(host, port, state, this::handleServerEvent);

        // Kết nối trong background thread
        new Thread(() -> {
            try {
                client.connect(playerName);

                // Chờ JOIN_ACK (max 5 giây)
                long timeout = System.currentTimeMillis() + 5000;
                while (!state.connected && System.currentTimeMillis() < timeout) {
                    Thread.sleep(50);
                }

                if (state.connected) {
                    // Chuyển sang màn hình game trên EDT
                    // (Tên người chơi đã được GameClient gán sau JOIN_ACK)
                    SwingUtilities.invokeLater(() -> {
                        switchToGame();
                        lobbyPanel.showStatus("✅ Đã kết nối!", new Color(0x4CAF50));
                    });
                } else {
                    // Timeout
                    SwingUtilities.invokeLater(() -> {
                        lobbyPanel.showStatus("❌ Không thể kết nối! Kiểm tra IP/Port.", new Color(0xFF4444));
                        lobbyPanel.setConnectEnabled(true);
                        client.disconnect();
                    });
                }
            } catch (IOException e) {
                SwingUtilities.invokeLater(() -> {
                    lobbyPanel.showStatus("❌ Lỗi: " + e.getMessage(), new Color(0xFF4444));
                    lobbyPanel.setConnectEnabled(true);
                });
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "Connect-Thread").start();
    }

    // ──────────────────────────────────────────────────────────
    //  Callback từ GameClient (sự kiện Server)
    // ──────────────────────────────────────────────────────────

    private void handleServerEvent(String header, String[] parts) {
        switch (header) {
            case "GAME_OVER":
                // Không cần xử lý thêm – GamePanel tự vẽ overlay
                break;
            case "FULL":
                SwingUtilities.invokeLater(() -> {
                    showLobby();
                    lobbyPanel.showStatus("⛔ Phòng đã đủ người! Hãy thử lại.", new Color(0xFF6B35));
                });
                break;
            default:
                break;
        }
    }

    // ──────────────────────────────────────────────────────────
    //  Chuyển màn hình
    // ──────────────────────────────────────────────────────────

    private void switchToGame() {
        // Rebuild GamePanel với client mới
        if (gamePanel != null) {
            gamePanel.stopRendering();
            rootPanel.remove(gamePanel);
        }
        gamePanel = new GamePanel(state, client, this);
        rootPanel.add(gamePanel, SCREEN_GAME);

        // Tính kích thước cửa sổ theo bản đồ + Sidebar bên trái (230px)
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
        if (gamePanel != null) {
            gamePanel.stopRendering();
        }
        if (client != null) {
            client.disconnect();
            client = null;
        }
        state.reset();

        // Kích thước lobby
        setPreferredSize(new Dimension(480, 560));
        pack();
        setLocationRelativeTo(null);

        lobbyPanel.setConnectEnabled(true);
        lobbyPanel.showStatus(" ", Color.WHITE);
        cardLayout.show(rootPanel, SCREEN_LOBBY);
    }

    // ──────────────────────────────────────────────────────────
    //  Entry Point
    // ──────────────────────────────────────────────────────────

    public static void main(String[] args) {
        // Look and Feel tối
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignored) {}

        // Bật font antialiasing
        System.setProperty("awt.useSystemAAFontSettings", "on");
        System.setProperty("swing.aatext", "true");

        SwingUtilities.invokeLater(() -> {
            GameApp app = new GameApp();
            app.setVisible(true);
        });
    }
}
