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

import java.io.IOException;
import java.lang.reflect.Method;
import java.time.format.DateTimeFormatter;

/**
 * Обновлённый MainMenuController — более тонкая ответственность: UI + делегирование сетевых задач
 * Сетевые сообщения теперь обрабатывает GameNetworkController (adapter), а чат — ChatService.
 */
public class MainMenuController implements GameNetworkController.NetworkListener {

    @FXML private TextArea chatArea;
    @FXML private TextField messageField;
    @FXML private TextField ipAddressField;
    @FXML private Label connectionStatus;

    // Состояние/сервисные поля
    private GameState lastGameState = null;
    private Client client;
    private Server server;
    private Thread serverThread;
    private boolean isServerCreated = false;
    private boolean isClientConnected = false;

    // Компоненты, вынесенные в отдельные классы
    private ChatService chatService;
    private GameNetworkController networkController;

    private final DateTimeFormatter timeFmt = DateTimeFormatter.ofPattern("HH:mm");

    @FXML
    public void initialize() {
        System.out.println("🏠 MainMenuController инициализирован");

        // Инициализируем сервис чата
        this.chatService = new ChatService(chatArea);

        // Приветственные сообщения
        chatService.addChatMessage("🎮 Система", "Добро пожаловать в Dungeon Mayhem!");
        chatService.addChatMessage("ℹ️ Система", "Выберите режим игры:");
        chatService.addChatMessage("ℹ️ Система", "1. Одиночная игра - просто нажмите 'Начать игру'");
        chatService.addChatMessage("ℹ️ Система", "2. Сетевая игра - создайте сервер и подключитесь к нему");

        // Дефолтный IP для удобства
        ipAddressField.setText("localhost");

        // Быстрый Enter для полей
        ipAddressField.setOnAction(e -> connectToServer());
        messageField.setOnAction(e -> sendMessage());

        updateConnectionStatus("Одиночный режим", false);
    }

    @FXML
    private void createServer() {
        try {
            if (isServerCreated) {
                chatService.addChatMessage("⚠️ Система", "Сервер уже запущен");
                return;
            }

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
        }
    }

    private void connectAsLocalhost() {
        Platform.runLater(() -> {
            ipAddressField.setText("localhost");
            connectToServer();
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
            showError("Введите IP адрес сервера");
            return;
        }

        try {
            // Создаём клиент как и раньше — затем оборачиваем в сетевой контроллер
            client = new Client(ip, 12345, this::rawClientMessageHandler);
            Thread clientThread = new Thread(client, "Client-Thread");
            clientThread.setDaemon(true);
            clientThread.start();

            // GameNetworkController зарегистрирует себя как обработчик низкоуровневых сообщений
            networkController = new GameNetworkController(client, this);

            updateConnectionStatus("🟡 Подключение...", false);
            chatService.addChatMessage("🔄 Система", "Подключение к " + ip + "...");

        } catch (IOException e) {
            showError("Ошибка создания клиента: " + e.getMessage());
        }
    }

    // Небольшой адаптер — клиент может посылать строковые подсказки до инициализации NetworkController.
    private void rawClientMessageHandler(Object message) {
        // Мы не рассчитываем на этот канал, но логируем на всякий случай
        System.out.println("[RAW CLIENT MSG] " + message);
    }

    // GameNetworkController.NetworkListener impl
    @Override
    public void onChatMessage(String sender, String message) {
        chatService.addChatMessage("💬 " + sender, message);
    }

    @Override
    public void onGameUpdate(GameState state) {
        // Сохраняем последнее состояние для передачи в GameController при старте игры
        this.lastGameState = state;
        chatService.addChatMessage("🎮 Система", "Сервер готов к игре!");
        System.out.println("[MAIN] Сохранено последнее состояние игры (GAME_UPDATE)");
    }

    @Override
    public void onCardPlayed(com.example.dungeon.game.Card card) {
        chatService.addChatMessage("🎴 Противник", "сыграл карту: " + card.getName());
    }

    @Override
    public void onConnected(String info) {
        isClientConnected = true;
        updateConnectionStatus("🟢 Подключено", true);
        chatService.addChatMessage("✅ Система", info);

        // Если сервер создан и второй игрок подключился — уведомим
        if (isServerCreated) {
            chatService.addChatMessage("🎮 Система", "Оба игрока подключены! Игра готова к запуску.");
        }
    }

    @Override
    public void onDisconnected(String reason) {
        isClientConnected = false;
        updateConnectionStatus("🔴 Отключено", false);
        chatService.addChatMessage("🔌 Система", reason);
    }

    @Override
    public void onError(String error) {
        isClientConnected = false;
        updateConnectionStatus("🔴 Ошибка", false);
        showError(error);
    }

    @FXML
    private void startGame() {
        System.out.println("🚀 Запуск игры...");

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/game.fxml"));

            // Сначала грузим FXML и получение контроллера через loader.getController()
            Parent root = loader.load();

            Object controller = loader.getController();
            // Если в контроллере есть методы для установки client / initialState, пробуем их вызвать рефлексией.
            if (controller != null) {
                try {
                    Method mClient = controller.getClass().getMethod("setClient", Client.class);
                    mClient.invoke(controller, client);
                } catch (NoSuchMethodException ignored) {
                    // контроллер не предоставляет setClient — нормально, пропускаем
                } catch (Exception ex) {
                    System.err.println("[MAIN] Ошибка вызова setClient: " + ex.getMessage());
                }

                try {
                    Method mState = controller.getClass().getMethod("setInitialGameState", GameState.class);
                    mState.invoke(controller, lastGameState);
                } catch (NoSuchMethodException ignored) {
                    // контроллер не предоставляет setInitialGameState — нормально, пропускаем
                } catch (Exception ex) {
                    System.err.println("[MAIN] Ошибка вызова setInitialGameState: " + ex.getMessage());
                }
            }

            Stage gameStage = new Stage();

            String title = "Dungeon Mayhem - ";
            if (client != null && isClientConnected) {
                title += "Сетевая битва!";
                chatService.addChatMessage("🎮 Система", "Запуск сетевой игры...");
            } else {
                title += "Одиночная игра";
                chatService.addChatMessage("🎮 Система", "Запуск одиночной игры...");
            }

            gameStage.setTitle(title);
            gameStage.setScene(new Scene(root, 1000, 700));
            gameStage.setMinWidth(800);
            gameStage.setMinHeight(600);

            // Попытаемся получить контроллер снова для вызова cleanup при закрытии
            Object ctrlForClose = loader.getController();
            gameStage.setOnCloseRequest(event -> {
                System.out.println("Закрытие игрового окна");
                try {
                    if (ctrlForClose != null) {
                        Method cleanup = null;
                        try {
                            cleanup = ctrlForClose.getClass().getMethod("cleanup");
                        } catch (NoSuchMethodException ignored) {}
                        if (cleanup != null) cleanup.invoke(ctrlForClose);
                    }
                } catch (Exception ex) {
                    System.err.println("[MAIN] Ошибка при cleanup игрового контроллера: " + ex.getMessage());
                }
                // Показываем обратно главное меню
                showMainMenu();
            });

            gameStage.show();

            // Скрываем главное окно, но не закрываем
            Stage mainStage = (Stage) chatArea.getScene().getWindow();
            mainStage.hide();

        } catch (IOException e) {
            showError("Ошибка загрузки игрового окна: " + e.getMessage());
            e.printStackTrace();
            showMainMenu();
        } catch (Exception e) {
            showError("Ошибка запуска игры: " + e.getMessage());
            e.printStackTrace();
            showMainMenu();
        }
    }

    private void showMainMenu() {
        try {
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
        if (message.isEmpty()) return;

        if (networkController != null && client != null && isClientConnected) {
            networkController.sendChat(message);
            chatService.addChatMessage("Вы", message);
        } else if (client != null && isClientConnected) {
            // В редком случае, если networkController не был инициализирован
            client.sendChatMessage(message);
            chatService.addChatMessage("Вы", message);
        } else {
            // Локальный режим — только эмуляция помощи
            chatService.addChatMessage("Вы", message);
            if (message.toLowerCase().contains("привет")) {
                chatService.addChatMessage("🤖 Система", "Привет! Создайте сервер или подключитесь к существующему для сетевой игры.");
            } else if (message.toLowerCase().contains("помощь") || message.contains("?")) {
                chatService.addChatMessage("🤖 Система", "Доступные команды:");
                chatService.addChatMessage("🤖 Система", "- Создать сервер: запускает игру для 2 игроков");
                chatService.addChatMessage("🤖 Система", "- Подключиться: подключение к серверу по IP");
                chatService.addChatMessage("🤖 Система", "- Начать игру: запуск одиночной или сетевой игры");
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

        updateConnectionStatus("Одиночный режим", false);
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

    // --- Новый метод showError (ранее отсутствовал) ---
    private void showError(String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Ошибка");
            alert.setHeaderText("Произошла ошибка");
            alert.setContentText(message);
            alert.showAndWait();
        });
    }
}
