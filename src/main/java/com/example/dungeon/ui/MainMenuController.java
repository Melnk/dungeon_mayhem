package com.example.dungeon.ui;

import com.example.dungeon.network.Client;
import com.example.dungeon.network.Server;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Stage;

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

    @FXML
    public void initialize() {
        // Инициализация чата
        addChatMessage("Система", "Добро пожаловать в Dungeon Mayhem!");
    }

    @FXML
    private void createServer() {
        try {
            server = new Server(12345);
            new Thread(server).start();
            connectionStatus.setText("Сервер запущен");
            connectionStatus.setStyle("-fx-text-fill: #4CAF50;");
            addChatMessage("Система", "Сервер запущен на порту 12345");
        } catch (IOException e) {
            showError("Ошибка запуска сервера: " + e.getMessage());
        }
    }

    @FXML
    private void connectToServer() {
        String ip = ipAddressField.getText().trim();
        if (ip.isEmpty()) {
            showError("Введите IP адрес сервера");
            return;
        }

        try {
            client = new Client(ip, 12345, this::handleNetworkMessage);
            new Thread(client).start();
            connectionStatus.setText("Подключено к " + ip);
            connectionStatus.setStyle("-fx-text-fill: #4CAF50;");
            addChatMessage("Система", "Подключено к серверу " + ip);
        } catch (IOException e) {
            showError("Ошибка подключения: " + e.getMessage());
        }
    }

    @FXML
    private void startGame() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/game.fxml"));
            Parent root = loader.load();

            Stage stage = new Stage();
            stage.setTitle("Dungeon Mayhem - Игра");
            stage.setScene(new Scene(root, 1000, 700));
            stage.show();

            // Закрываем главное меню
            ((Stage) chatArea.getScene().getWindow()).close();
        } catch (IOException e) {
            showError("Ошибка запуска игры: " + e.getMessage());
        }
    }

    @FXML
    private void sendMessage() {
        String message = messageField.getText().trim();
        if (!message.isEmpty() && client != null) {
            // Здесь будет отправка сообщения через сеть
            addChatMessage("Вы", message);
            messageField.clear();
        }
    }

    private void handleNetworkMessage(Object message) {
        // Обработка сетевых сообщений будет реализована позже
        System.out.println("Получено сообщение: " + message);
    }

    public void addChatMessage(String sender, String message) {
        String time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"));
        String formattedMessage = String.format("[%s] %s: %s\n", time, sender, message);

        javafx.application.Platform.runLater(() -> {
            chatArea.appendText(formattedMessage);
        });
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Ошибка");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
