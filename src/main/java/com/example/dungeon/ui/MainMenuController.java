package com.example.dungeon.ui;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Stage;
import com.example.dungeon.network.*;
import javafx.application.Platform;

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
    private boolean isServerCreated = false;
    private boolean isClientConnected = false;

    @FXML
    public void initialize() {
        System.out.println("🏠 MainMenuController инициализирован");

        // Инициализация чата
        addChatMessage("🎮 Система", "Добро пожаловать в Dungeon Mayhem!");
        addChatMessage("ℹ️ Система", "Выберите режим игры:");
        addChatMessage("ℹ️ Система", "1. Одиночная игра - просто нажмите 'Начать игру'");
        addChatMessage("ℹ️ Система", "2. Сетевая игра - создайте сервер и подключитесь к нему");

        // Устанавливаем дефолтный IP (localhost для тестирования)
        ipAddressField.setText("localhost");

        // Устанавливаем обработчик для поля IP (автоматическое подключение при нажатии Enter)
        ipAddressField.setOnAction(event -> connectToServer());

        // Устанавливаем обработчик для поля сообщения
        messageField.setOnAction(event -> sendMessage());
    }

    @FXML
    private void createServer() {
        try {
            if (isServerCreated) {
                addChatMessage("⚠️ Система", "Сервер уже запущен");
                return;
            }

            server = new Server(12345);
            serverThread = new Thread(server, "Server-Thread");
            serverThread.setDaemon(true); // Демон-поток (завершится с программой)
            serverThread.start();

            isServerCreated = true;
            updateConnectionStatus("🟢 Сервер запущен", true);

            addChatMessage("✅ Система", "Сервер запущен на порту 12345");
            addChatMessage("⏳ Система", "Ожидание второго игрока...");

            // Если мы создали сервер, автоматически подключаемся к нему как клиент
            connectAsLocalhost();

        } catch (IOException e) {
            showError("Ошибка запуска сервера: " + e.getMessage());
        }
    }

    private void connectAsLocalhost() {
        // Автоматическое подключение к localhost после создания сервера
        Platform.runLater(() -> {
            ipAddressField.setText("localhost");
            connectToServer();
        });
    }

    @FXML
    private void connectToServer() {
        if (isClientConnected) {
            addChatMessage("⚠️ Система", "Уже подключено к серверу");
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

            updateConnectionStatus("🟡 Подключение...", false);
            addChatMessage("🔄 Система", "Подключение к " + ip + "...");

        } catch (IOException e) {
            showError("Ошибка создания клиента: " + e.getMessage());
        }
    }

    private void handleNetworkMessage(Object message) {
        if (message instanceof String) {
            String msg = (String) message;
            if (msg.startsWith("CONNECTED:")) {
                isClientConnected = true;
                updateConnectionStatus("🟢 Подключено", true);
                addChatMessage("✅ Система", msg.substring(10));

                // Автоматически начинаем игру при подключении второго игрока
                if (msg.contains("Игрок 2") && isServerCreated) {
                    addChatMessage("🎮 Система", "Оба игрока подключены! Игра готова к запуску.");
                }

            } else if (msg.startsWith("DISCONNECTED:")) {
                isClientConnected = false;
                updateConnectionStatus("🔴 Отключено", false);
                addChatMessage("🔌 Система", msg.substring(13));

            } else if (msg.startsWith("ERROR:")) {
                isClientConnected = false;
                updateConnectionStatus("🔴 Ошибка", false);
                showError(msg.substring(6));

            } else if (msg.startsWith("PLAYER:")) {
                String playerInfo = msg.substring(7);
                addChatMessage("👤 Система", playerInfo);
            }
        } else if (message instanceof NetworkMessage) {
            NetworkMessage networkMessage = (NetworkMessage) message;
            handleNetworkMessageType(networkMessage);
        }
    }

    private void handleNetworkMessageType(NetworkMessage message) {
        Platform.runLater(() -> {
            try {
                switch (message.getType()) {
                    case CHAT_MESSAGE:
                        String chatMsg = (String) message.getData();
                        // Проверяем, не наше ли это сообщение
                        if (!chatMsg.startsWith("Вы:") && !chatMsg.contains("Вы:")) {
                            addChatMessage("💬 Игрок", chatMsg);
                        }
                        break;

                    case PLAYER_JOIN:
                        String joinMsg = (String) message.getData();
                        addChatMessage("👥 Система", joinMsg);
                        break;

                    case GAME_UPDATE:
                        // При обновлении игры можно предложить начать игру
                        addChatMessage("🎮 Система", "Сервер готов к игре!");
                        break;

                    default:
                        System.out.println("Получено сообщение: " + message.getType());
                }
            } catch (Exception e) {
                System.err.println("Ошибка обработки сетевого сообщения: " + e.getMessage());
            }
        });
    }

    @FXML
    private void startGame() {
        System.out.println("🚀 Запуск игры...");

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/game.fxml"));

            // Передаем клиент в GameController (может быть null для одиночной игры)
            GameController gameController = new GameController(client);
            loader.setControllerFactory(param -> gameController);

            Parent root = loader.load();

            Stage gameStage = new Stage();

            // Определяем заголовок в зависимости от режима
            String title = "Dungeon Mayhem - ";
            if (client != null && isClientConnected) {
                title += "Сетевая битва!";
                addChatMessage("🎮 Система", "Запуск сетевой игры...");
            } else {
                title += "Одиночная игра";
                addChatMessage("🎮 Система", "Запуск одиночной игры...");
            }

            gameStage.setTitle(title);
            gameStage.setScene(new Scene(root, 1000, 700));
            gameStage.setMinWidth(800);
            gameStage.setMinHeight(600);

            // Обработка закрытия окна игры
            gameStage.setOnCloseRequest(event -> {
                System.out.println("Закрытие игрового окна");
                if (gameController != null) {
                    gameController.cleanup();
                }

                // Не закрываем главное меню, чтобы можно было начать новую игру
                // Возвращаемся в главное меню
                showMainMenu();
            });

            // Показываем игровое окно
            gameStage.show();

            // Не закрываем главное меню, а просто скрываем его
            Stage mainStage = (Stage) chatArea.getScene().getWindow();
            mainStage.hide();

        } catch (Exception e) {
            showError("Ошибка запуска игры: " + e.getMessage());
            e.printStackTrace();

            // Показываем главное меню обратно в случае ошибки
            showMainMenu();
        }
    }

    private void showMainMenu() {
        try {
            // Показываем скрытое главное меню
            Stage mainStage = (Stage) chatArea.getScene().getWindow();
            mainStage.show();
            mainStage.toFront();
        } catch (Exception e) {
            System.err.println("Ошибка при показе главного меню: " + e.getMessage());
        }
    }

    @FXML
    private void sendMessage() {
        String message = messageField.getText().trim();
        if (message.isEmpty()) {
            return;
        }

        if (client != null && isClientConnected) {
            // Сетевое сообщение
            client.sendChatMessage(message);
            addChatMessage("💬 Вы", message);
        } else {
            // Локальное сообщение (для чата в главном меню)
            addChatMessage("💬 Вы", message);

            // Имитация ответа системы в одиночном режиме
            if (message.toLowerCase().contains("привет")) {
                addChatMessage("🤖 Система", "Привет! Создайте сервер или подключитесь к существующему для сетевой игры.");
            } else if (message.toLowerCase().contains("помощь") || message.contains("?")) {
                addChatMessage("🤖 Система", "Доступные команды:");
                addChatMessage("🤖 Система", "- Создать сервер: запускает игру для 2 игроков");
                addChatMessage("🤖 Система", "- Подключиться: подключение к серверу по IP");
                addChatMessage("🤖 Система", "- Начать игру: запуск одиночной или сетевой игры");
            }
        }

        messageField.clear();
        messageField.requestFocus();
    }

    private void updateConnectionStatus(String status, boolean isGood) {
        Platform.runLater(() -> {
            connectionStatus.setText(status);
            if (isGood) {
                connectionStatus.setStyle("-fx-text-fill: #4CAF50; -fx-font-weight: bold;");
            } else {
                connectionStatus.setStyle("-fx-text-fill: #FF9800; -fx-font-weight: bold;");
            }
        });
    }

    public void addChatMessage(String sender, String message) {
        String time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"));
        String formattedMessage = String.format("[%s] %s: %s\n", time, sender, message);

        Platform.runLater(() -> {
            chatArea.appendText(formattedMessage);
            // Авто-скролл к последнему сообщению
            chatArea.setScrollTop(Double.MAX_VALUE);
        });
    }

    private void showError(String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Ошибка");
            alert.setHeaderText("Произошла ошибка");
            alert.setContentText(message);
            alert.showAndWait();
        });
    }

    @FXML
    private void clearChat() {
        Platform.runLater(() -> {
            chatArea.clear();
            addChatMessage("🧹 Система", "Чат очищен");
        });
    }

    @FXML
    private void showHelp() {
        String helpText = """
            🎮 DUNGEON MAYHEM - СПРАВКА 🎮

            📋 РЕЖИМЫ ИГРЫ:

            1. ОДИНОЧНАЯ ИГРА:
               • Нажмите "Начать игру" без создания сервера
               • Игра против компьютерного противника
               • Идеально для обучения и тестирования

            2. СЕТЕВАЯ ИГРА (2 игрока):
               • Игрок 1: Нажмите "Создать сервер"
               • Игрок 2: Введите IP адрес и нажмите "Подключиться"
               • Оба игрока: Нажмите "Начать игру"

            🌐 СЕТЕВЫЕ НАСТРОЙКИ:
            • Порт по умолчанию: 12345
            • Для игры на одном компьютере: используйте "localhost"
            • Для игры по сети: используйте IP адрес компьютера с сервером

            🎯 КАК НАЧАТЬ:
            1. Выберите режим игры
            2. Если сетевая игра - дождитесь подключения обоих игроков
            3. Нажмите "Начать игру"
            4. В игре: кликайте по картам для атаки, защиты или лечения

            ❓ ПОЛУЧИТЬ ПОМОЩЬ:
            • Напишите в чат "помощь" или "?"
            • Или обратитесь к преподавателю

            Удачи в подземелье! ⚔️
            """;

        TextArea textArea = new TextArea(helpText);
        textArea.setEditable(false);
        textArea.setWrapText(true);
        textArea.setMaxWidth(500);
        textArea.setMaxHeight(400);

        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Справка");
        alert.setHeaderText("Dungeon Mayhem - Руководство");
        alert.getDialogPane().setContent(textArea);
        alert.setResizable(true);
        alert.getDialogPane().setPrefSize(520, 450);
        alert.showAndWait();
    }

    // Метод для очистки ресурсов
    public void cleanup() {
        System.out.println("🧹 Очистка ресурсов MainMenuController");

        if (client != null) {
            client.stop();
            client = null;
        }
        if (server != null) {
            server.shutdown();
            server = null;
        }

        isServerCreated = false;
        isClientConnected = false;
    }

    // Метод вызывается при закрытии приложения
    @FXML
    private void exitApplication() {
        cleanup();
        Platform.exit();
    }

    // Метод для получения текущего состояния
    public String getConnectionStatus() {
        if (isClientConnected && isServerCreated) {
            return "Сервер + Клиент (Игрок 1)";
        } else if (isClientConnected) {
            return "Клиент (Игрок 2)";
        } else if (isServerCreated) {
            return "Только сервер";
        } else {
            return "Одиночный режим";
        }
    }
}
