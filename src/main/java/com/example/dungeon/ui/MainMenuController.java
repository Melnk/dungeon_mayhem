package com.example.dungeon.ui;

import com.example.dungeon.game.GameState;
import com.example.dungeon.network.*;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Stage;
import lombok.Getter;

import java.io.IOException;
import java.lang.reflect.Method;
import java.time.format.DateTimeFormatter;

/**
 * Главное меню - отвечает ТОЛЬКО за UI и координацию.
 * Сетевая логика делегирована MenuNetworkHandler.
 */
public class MainMenuController {

    @FXML private TextArea chatArea;
    @FXML private TextField messageField;
    @FXML private TextField ipAddressField;
    @FXML private Label connectionStatus;

    // Состояние/сервисные поля
    @Getter
    private GameState lastGameState = null;
    private Client client;
    private Server server;
    private Thread serverThread;
    private boolean isServerCreated = false;
    private boolean isClientConnected = false;

    // Компоненты, вынесенные в отдельные классы
    private ChatService chatService;
    private GameNetworkController networkController;
    private MenuNetworkHandler networkHandler;

    @FXML
    public void initialize() {
        System.out.println("🏠 MainMenuController инициализирован");

        // Инициализируем сервисы
        this.chatService = new ChatService(chatArea);
        this.networkHandler = new MenuNetworkHandler(this);

        // Приветственные сообщения
        chatService.addChatMessage("🎮 Система", "Добро пожаловать в Dungeon Mayhem!");
        chatService.addChatMessage("⚔️ Система", "Эпическая битва в подземельях ждет вас!");

        // Задержка для анимированного появления
        Platform.runLater(() -> {
            try {
                Thread.sleep(500);
                chatService.addChatMessage("ℹ️ Система", "Выберите режим игры:");

                Thread.sleep(500);
                chatService.addChatMessage("ℹ️ Система", "1. Одиночная игра - нажмите 'Начать игру'");

                Thread.sleep(500);
                chatService.addChatMessage("ℹ️ Система", "2. Сетевая игра - создайте сервер и подключитесь");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        // Дефолтный IP для удобства
        ipAddressField.setText("localhost");

        // Быстрый Enter для полей
        ipAddressField.setOnAction(e -> connectToServer());
        messageField.setOnAction(e -> sendMessage());

        updateConnectionStatus("⚪ Не подключено", false);
    }

    @FXML
    private void createServer() {
        try {
            if (isServerCreated) {
                chatService.addChatMessage("⚠️ Система", "Сервер уже запущен");
                return;
            }

            chatService.addChatMessage("🔄 Система", "Запуск сервера...");

            server = new Server(12345);
            serverThread = new Thread(server, "Server-Thread");
            serverThread.setDaemon(true);
            serverThread.start();

            isServerCreated = true;
            updateConnectionStatus("🟢 Сервер запущен", true);

            chatService.addChatMessage("✅ Система", "Сервер запущен на порту 12345");
            chatService.addChatMessage("⏳ Система", "Ожидание второго игрока...");

            // Автоматически подключаемся как локальный клиент
            connectAsLocalhost();

        } catch (IOException e) {
            showError("Ошибка запуска сервера: " + e.getMessage());
            updateConnectionStatus("🔴 Ошибка", false);
        }
    }

    private void connectAsLocalhost() {
        Platform.runLater(() -> {
            ipAddressField.setText("localhost");
            // Даем серверу время запуститься
            javafx.animation.PauseTransition pause = new javafx.animation.PauseTransition(
                javafx.util.Duration.seconds(1.5));
            pause.setOnFinished(e -> connectToServer());
            pause.play();
        });
    }

    @FXML
    private void connectToServer() {
        if (isClientConnected) {
            chatService.addChatMessage("⚠️ Система", "Уже подключено к серверу");
            return;
        }

        String ip = ipAddressField.getText().trim();
        if (ip.isEmpty()) {
            ip = "localhost"; // Значение по умолчанию
        }

        try {
            chatService.addChatMessage("🔄 Система", "Подключение к " + ip + "...");

            // Создаём клиент
            client = new Client(ip, 12345, null);

            // Создаем GameNetworkController с нашим handler
            networkController = new GameNetworkController(client, networkHandler);

            // Запускаем клиент в отдельном потоке
            Thread clientThread = new Thread(client, "Client-Thread");
            clientThread.setDaemon(true);
            clientThread.start();

            updateConnectionStatus("🟡 Подключение...", false);

            // Проверяем подключение через секунду
            new Thread(() -> {
                try {
                    Thread.sleep(1500);
                    if (client != null && client.isConnected()) {
                        Platform.runLater(() -> {
                            chatService.addChatMessage("✅ Система", "Успешно подключено к ");
                            updateConnectionStatus("🟢 Подключено", true);
                            isClientConnected = true;
                        });
                    } else {
                        Platform.runLater(() -> {
                            chatService.addChatMessage("❌ Система", "Не удалось подключиться к ");
                            updateConnectionStatus("🔴 Ошибка подключения", false);
                            isClientConnected = false;
                        });
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }).start();

        } catch (IOException e) {
            showError("Ошибка создания клиента: " + e.getMessage());
            updateConnectionStatus("🔴 Ошибка", false);
            isClientConnected = false;
        }
    }

    // === Методы, вызываемые MenuNetworkHandler ===

    public void addChatMessage(String sender, String message) {
        Platform.runLater(() -> {
            // Если отправитель пустой или "Игрок", форматируем по-другому
            if (sender == null || sender.isEmpty() || sender.equals("Игрок")) {
                chatService.addChatMessage("", message);
            } else {
                chatService.addChatMessage(sender, message);
            }
        });
    }

    public void handleGameUpdate(GameState state) {
        Platform.runLater(() -> {
            this.lastGameState = state;
            chatService.addChatMessage("🎮 Система", "Сервер готов к игре!");
            System.out.println("[MAIN] Сохранено последнее состояние игры (GAME_UPDATE)");
        });
    }

    public void handleConnectionStatus(boolean connected, String message) {
        Platform.runLater(() -> {
            this.isClientConnected = connected;

            if (connected) {
                updateConnectionStatus("🟢 Подключено", true);
                chatService.addChatMessage("✅ Система", message);

                if (isServerCreated) {
                    chatService.addChatMessage("🎮 Система", "Оба игрока подключены! Игра готова к запуску.");
                }
            } else {
                updateConnectionStatus("🔴 Отключено", false);
                chatService.addChatMessage("🔌 Система", message);
            }
        });
    }

    public void handleNetworkError(String error) {
        Platform.runLater(() -> {
            this.isClientConnected = false;
            updateConnectionStatus("🔴 Ошибка", false);
            showError(error);
        });
    }

    // === Методы для GameController ===

    @FXML
    private void startGame() {
        System.out.println("🚀 Запуск игры...");

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/game.fxml"));
            Parent root = loader.load();

            // Получаем контроллер игры и настраиваем его
            Object controller = loader.getController();

            // Настраиваем клиент (если есть)
            if (client != null && isClientConnected) {
                try {
                    Method mClient = controller.getClass().getMethod("setClient", Client.class);
                    mClient.invoke(controller, client);
                } catch (NoSuchMethodException ignored) {
                    // Контроллер не предоставляет setClient
                }
            }

            // Передаём начальное состояние (если есть)
            if (lastGameState != null) {
                try {
                    Method mState = controller.getClass().getMethod("setInitialGameState", GameState.class);
                    mState.invoke(controller, lastGameState);
                } catch (NoSuchMethodException ignored) {
                    // Контроллер не предоставляет setInitialGameState
                }
            }

            // Создаём сцену игры
            Stage gameStage = new Stage();
            String title = "Dungeon Mayhem - " +
                (client != null && isClientConnected ? "Сетевая битва!" : "Одиночная игра");

            gameStage.setTitle(title);
            gameStage.setScene(new Scene(root, 1200, 800)); // Увеличили размер
            gameStage.setMinWidth(1000);
            gameStage.setMinHeight(700);

            // Настраиваем обработчик закрытия окна
            setupGameStageCloseHandler(gameStage, controller);

            gameStage.show();

            // Скрываем главное окно
            Stage mainStage = (Stage) chatArea.getScene().getWindow();
            mainStage.hide();

        } catch (IOException e) {
            showError("Ошибка загрузки игрового окна: " + e.getMessage());
            e.printStackTrace();
        } catch (Exception e) {
            showError("Ошибка запуска игры: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void setupGameStageCloseHandler(Stage gameStage, Object controller) {
        gameStage.setOnCloseRequest(event -> {
            System.out.println("Закрытие игрового окна");

            // Вызываем cleanup у контроллера игры (если есть метод)
            if (controller != null) {
                try {
                    Method cleanup = controller.getClass().getMethod("cleanup");
                    cleanup.invoke(controller);
                } catch (NoSuchMethodException ignored) {
                    // Метода cleanup нет - это нормально
                } catch (Exception e) {
                    System.err.println("[MAIN] Ошибка при cleanup игрового контроллера: " + e.getMessage());
                }
            }

            // Показываем главное меню обратно
            showMainMenu();
        });
    }

    public static void showMainMenu() {
        try {
            FXMLLoader loader = new FXMLLoader(MainMenuController.class.getResource("/fxml/mainMenu.fxml"));
            Parent root = loader.load();
            Stage stage = new Stage();
            stage.setTitle("Dungeon Mayhem - Главное меню");
            stage.setScene(new Scene(root, 900, 700)); // Увеличили размер
            stage.show();
        } catch (IOException e) {
            System.err.println("Ошибка при показе главного меню: " + e.getMessage());
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Ошибка");
            alert.setHeaderText("Не удалось открыть главное меню");
            alert.setContentText(e.getMessage());
            alert.showAndWait();
        }
    }

    @FXML
    private void sendMessage() {
        String message = messageField.getText().trim();
        if (message.isEmpty()) return;

        // Очищаем поле сразу
        messageField.clear();
        messageField.requestFocus();

        // Если есть клиент и он подключен, отправляем через сеть
        if (client != null && isClientConnected) {
            if (networkController != null) {
                networkController.sendChat(message);
                // Сообщение добавится через сеть
            } else if (client.isConnected()) {
                client.sendChatMessage(message);
                // Сообщение добавится через сеть
            }
        } else {
            // Локальный режим - просто добавляем в чат
            chatService.addChatMessage("Вы", message);
            handleLocalCommands(message);
        }
    }

    private void handleLocalCommands(String message) {
        String lowerMessage = message.toLowerCase();

        if (lowerMessage.contains("привет") || lowerMessage.contains("hello")) {
            chatService.addChatMessage("🤖 Система", "Приветствую, искатель приключений! Создайте сервер или подключитесь к нему.");
        } else if (lowerMessage.contains("помощь") || lowerMessage.contains("help") || lowerMessage.contains("?")) {
            showHelpDialog();
        } else if (lowerMessage.contains("статус") || lowerMessage.contains("status")) {
            String status = getConnectionStatus();
            chatService.addChatMessage("🤖 Система", "Статус: " + status);
        } else if (lowerMessage.contains("команды") || lowerMessage.contains("commands")) {
            chatService.addChatMessage("🤖 Система", "Доступные команды:");
            chatService.addChatMessage("🤖 Система", "- привет/hello - приветствие");
            chatService.addChatMessage("🤖 Система", "- помощь/help/? - показать справку");
            chatService.addChatMessage("🤖 Система", "- статус/status - показать статус подключения");
            chatService.addChatMessage("🤖 Система", "- персонажи - информация о персонажах");
        } else if (lowerMessage.contains("персонажи") || lowerMessage.contains("герои")) {
            chatService.addChatMessage("🤖 Система", "Доступные персонажи:");
            chatService.addChatMessage("🤖 Система", "⚔️ Варвар - сильный воин с повышенным уроном");
            chatService.addChatMessage("🤖 Система", "🛡️ Паладин - защитник с усиленной защитой");
            chatService.addChatMessage("🤖 Система", "🗡️ Плут - хитрый боец с критическими ударами");
            chatService.addChatMessage("🤖 Система", "🔮 Маг - волшебник с усиленным лечением");
        }
    }

    private void showHelpDialog() {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Справка");
            alert.setHeaderText("Dungeon Mayhem - Справка");

            TextArea textArea = new TextArea();
            textArea.setText("🎮 ДОСТУПНЫЕ КОМАНДЫ:\n\n" +
                "💬 В ЧАТЕ:\n" +
                "• привет / hello - приветствие\n" +
                "• помощь / help / ? - показать справку\n" +
                "• статус / status - статус подключения\n" +
                "• персонажи - информация о персонажах\n" +
                "• команды - список команд\n\n" +
                "🎯 УПРАВЛЕНИЕ:\n" +
                "1. СОЗДАТЬ СЕРВЕР - запуск сервера для игры\n" +
                "2. ПОДКЛЮЧИТЬСЯ - подключение к серверу по IP\n" +
                "3. НАЧАТЬ ИГРУ - запуск одиночной или сетевой игры\n\n" +
                "🌐 СЕТЬ:\n" +
                "• По умолчанию: localhost:12345\n" +
                "• Для игры в сети: используйте IP компьютера с сервером");
            textArea.setEditable(false);
            textArea.setWrapText(true);
            textArea.setPrefSize(400, 350);

            alert.getDialogPane().setContent(textArea);
            alert.showAndWait();
        });
    }

    private void updateConnectionStatus(String status, boolean isGood) {
        Platform.runLater(() -> {
            connectionStatus.setText(status);
            if (isGood) {
                connectionStatus.setStyle("-fx-text-fill: #66ff66; -fx-font-weight: bold; " +
                    "-fx-effect: dropshadow(gaussian, #00cc00, 3, 0, 0, 1);");
            } else if (status.contains("Ошибка") || status.contains("Не удалось")) {
                connectionStatus.setStyle("-fx-text-fill: #ff6666; -fx-font-weight: bold; " +
                    "-fx-effect: dropshadow(gaussian, #cc0000, 3, 0, 0, 1);");
            } else {
                connectionStatus.setStyle("-fx-text-fill: #ffcc66; -fx-font-weight: bold; " +
                    "-fx-effect: dropshadow(gaussian, #996600, 3, 0, 0, 1);");
            }
        });
    }

    @FXML
    private void clearChat() {
        Platform.runLater(() -> {
            chatArea.clear();
            chatService.addChatMessage("🧹 Система", "Чат очищен");
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

        if (networkController != null) {
            networkController.shutdown();
            networkController = null;
        }

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

        updateConnectionStatus("⚪ Не подключено", false);
    }

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

    // Вспомогательные методы

    private void showError(String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Ошибка");
            alert.setHeaderText("Произошла ошибка");
            alert.setContentText(message);
            alert.showAndWait();
        });
    }

    // Геттеры для тестирования
    public boolean isServerCreated() {
        return isServerCreated;
    }

    public boolean isClientConnected() {
        return isClientConnected;
    }
}
