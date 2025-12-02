package com.example.dungeon.ui;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Stage;
import com.example.dungeon.network.*;

import java.io.IOException;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public class MainMenuController {

    @FXML private TextArea chatArea;
    @FXML private TextField messageField;
    @FXML private TextField ipAddressField;
    @FXML private Label connectionStatus;

    private Client client;
    private Server server;
    private Thread serverThread;

    @FXML
    public void initialize() {
        // Инициализация чата
        addChatMessage("Система", "Добро пожаловать в Dungeon Mayhem!");

        // Устанавливаем дефолтный IP (localhost для тестирования)
        ipAddressField.setText("localhost");
    }

    @FXML
    private void createServer() {
        try {
            if (server != null) {
                showError("Сервер уже запущен");
                return;
            }

            server = new Server(12345);
            serverThread = new Thread(server, "Server-Thread");
            serverThread.setDaemon(true); // Демон-поток (завершится с программой)
            serverThread.start();

            connectionStatus.setText("Сервер запущен");
            connectionStatus.setStyle("-fx-text-fill: #4CAF50;");
            addChatMessage("Система", "Сервер запущен на порту 12345");
            addChatMessage("Система", "Ожидание второго игрока...");

        } catch (IOException e) {
            showError("Ошибка запуска сервера: " + e.getMessage());
        }
    }

    @FXML
    private void connectToServer() {
        if (client != null && client.isConnected()) {
            showError("Уже подключено к серверу");
            return;
        }

        String ip = ipAddressField.getText().trim();
        if (ip.isEmpty()) {
            showError("Введите IP адрес сервера");
            return;
        }

        try {
            // Создаем клиент с обработчиком сообщений
            client = new Client(ip, 12345, this::handleNetworkMessage);
            Thread clientThread = new Thread(client, "Client-Thread");
            clientThread.setDaemon(true);
            clientThread.start();

            connectionStatus.setText("Подключение...");
            connectionStatus.setStyle("-fx-text-fill: #FF9800;");

        } catch (IOException e) {
            showError("Ошибка создания клиента: " + e.getMessage());
        }
    }

    private void handleNetworkMessage(Object message) {
        if (message instanceof String) {
            String msg = (String) message;
            if (msg.startsWith("CONNECTED:")) {
                updateConnectionStatus("Подключено", true);
                addChatMessage("Система", msg.substring(10));
            } else if (msg.startsWith("DISCONNECTED:")) {
                updateConnectionStatus("Отключено", false);
                addChatMessage("Система", msg.substring(13));
            } else if (msg.startsWith("ERROR:")) {
                updateConnectionStatus("Ошибка", false);
                showError(msg.substring(6));
            }
        } else if (message instanceof NetworkMessage) {
            NetworkMessage networkMessage = (NetworkMessage) message;
            handleNetworkMessageType(networkMessage);
        }
    }

    private void handleNetworkMessageType(NetworkMessage message) {
        javafx.application.Platform.runLater(() -> {
            switch (message.getType()) {
                case CHAT_MESSAGE:
                    String chatMsg = (String) message.getData();
                    addChatMessage("Игрок", chatMsg);
                    break;

                case PLAYER_JOIN:
                    String joinMsg = (String) message.getData();
                    addChatMessage("Система", joinMsg);
                    break;

                case GAME_UPDATE:
                    // Когда придет обновление игры, можно начать игру
                    addChatMessage("Система", "Игра начинается!");
                    // Здесь можно автоматически перейти к игре
                    break;

                default:
                    System.out.println("Получено сообщение: " + message.getType());
            }
        });
    }

    @FXML
    private void startGame() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/game.fxml"));

            // Создаем контроллер с передачей клиента
            GameController gameController = new GameController(client);
            loader.setControllerFactory(param -> new GameController(client));
            Parent root = loader.load();

            Stage gameStage = new Stage();
            gameStage.setTitle("Dungeon Mayhem - Битва!");
            gameStage.setScene(new Scene(root, 1000, 700));
            gameStage.setOnCloseRequest(event -> {
                if (client != null) {
                    // Временно комментируем, чтобы не закрывать соединение
                    // client.stop();
                }
            });
            gameStage.show();

            // Закрываем главное меню
            Stage mainStage = (Stage) chatArea.getScene().getWindow();
            mainStage.close();

        } catch (IOException e) {
            showError("Ошибка запуска игры: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @FXML
    private void sendMessage() {
        String message = messageField.getText().trim();
        if (!message.isEmpty()) {
            if (client != null && client.isConnected()) {
                client.sendChatMessage(message);
                addChatMessage("Вы", message);
                messageField.clear();
            } else {
                addChatMessage("Вы", message);
                addChatMessage("Система", "(Сообщение не отправлено - нет подключения)");
                messageField.clear();
            }
        }
    }

    private void updateConnectionStatus(String status, boolean isConnected) {
        javafx.application.Platform.runLater(() -> {
            connectionStatus.setText(status);
            if (isConnected) {
                connectionStatus.setStyle("-fx-text-fill: #4CAF50;");
            } else {
                connectionStatus.setStyle("-fx-text-fill: #ff4444;");
            }
        });
    }

    public void addChatMessage(String sender, String message) {
        String time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"));
        String formattedMessage = String.format("[%s] %s: %s\n", time, sender, message);

        javafx.application.Platform.runLater(() -> {
            chatArea.appendText(formattedMessage);
            // Авто-скролл к последнему сообщению
            chatArea.setScrollTop(Double.MAX_VALUE);
        });
    }

    private void showError(String message) {
        javafx.application.Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Ошибка");
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }

    // Метод для очистки ресурсов
    public void cleanup() {
        if (client != null) {
            client.stop();
        }
        if (server != null) {
            server.shutdown();
        }
    }
}
