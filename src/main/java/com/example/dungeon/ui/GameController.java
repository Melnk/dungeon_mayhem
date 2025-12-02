package com.example.dungeon.ui;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.canvas.*;
import javafx.scene.paint.Color;
import javafx.scene.text.TextAlignment;
import javafx.animation.*;
import javafx.stage.Stage;
import javafx.util.Duration;
import com.example.dungeon.network.*;
import com.example.dungeon.game.*;
import javafx.application.Platform;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Consumer;

public class GameController {

    // FXML элементы
    @FXML private Label playerHealthLabel;
    @FXML private Label playerShieldLabel;
    @FXML private Label opponentHealthLabel;
    @FXML private Label opponentShieldLabel;
    @FXML private Label gameStatusLabel;
    @FXML private Label turnIndicator;
    @FXML private Label lastActionLabel;

    @FXML private Canvas playerHealthCanvas;
    @FXML private Canvas opponentHealthCanvas;
    @FXML private Canvas battleAnimationCanvas;

    @FXML private HBox playerCardsContainer;
    @FXML private HBox opponentCardsContainer;

    @FXML private TextArea gameChatArea;
    @FXML private TextField gameMessageField;

    // Игровые переменные
    private Client client;
    private GameState currentGameState;
    private boolean isMyTurn = true; // Начинаем с нашего хода для теста
    private String playerName = "Вы";
    private String opponentName = "Противник";

    // Карты в руке
    private List<Card> playerHand = new ArrayList<>();
    private List<Pane> cardPanes = new ArrayList<>();

    // Тестовые значения здоровья
    private int playerHP = 10;
    private int playerShield = 0;
    private int opponentHP = 10;
    private int opponentShield = 0;

    // Сохраненный обработчик сообщений из MainMenu
    private Consumer<Object> originalMessageHandler;

    public GameController(Client client) {
        this.client = client;
        if (client != null) {
            // Сохраняем оригинальный обработчик
            this.originalMessageHandler = client.messageHandler;
        }
    }

    @FXML
    public void initialize() {
        System.out.println("🎮 GameController инициализирован");

        // Устанавливаем свой обработчик сообщений
        if (client != null && client.messageHandler != null) {
            this.originalMessageHandler = client.messageHandler;
            client.messageHandler = this::handleNetworkMessage;
        }

        // Инициализируем интерфейс
        initializeUI();

        // Добавляем тестовые карты для отладки
        addTestCards();

        // Отправляем сообщение о начале игры
        addChatMessage("⚔ Система", "Битва началась! Добро пожаловать в подземелье!");
        addChatMessage("⚔ Система", "Ваш ход! Выберите карту для атаки, защиты или лечения.");

        // Обновляем статус
        updateTurnIndicator();
        updateHealthDisplay();
    }

    private void initializeUI() {
        // Очищаем контейнеры карт
        playerCardsContainer.getChildren().clear();
        opponentCardsContainer.getChildren().clear();

        // Устанавливаем начальные значения
        updateHealthDisplay();
        gameStatusLabel.setText("🎯 ПОДГОТОВКА К БИТВЕ");
        turnIndicator.setText("Определяем очередность...");

        // Настраиваем чат
        gameChatArea.setWrapText(true);
        gameChatArea.setEditable(false);

        // Создаем скрытые карты противника
        for (int i = 0; i < 5; i++) {
            Pane hiddenCard = createHiddenCard();
            opponentCardsContainer.getChildren().add(hiddenCard);
        }
    }

    private void addTestCards() {
        // Очищаем текущие карты
        playerHand.clear();
        cardPanes.clear();
        playerCardsContainer.getChildren().clear();

        // Тестовые карты для отладки интерфейса
        Card[] testCards = {
            new Card(CardType.ATTACK, "Огненный шар"),
            new Card(CardType.DEFENSE, "Железный щит"),
            new Card(CardType.HEAL, "Целебное зелье"),
            new Card(CardType.ATTACK, "Удар кинжалом"),
            new Card(CardType.DEFENSE, "Магический барьер"),
            new Card(CardType.HEAL, "Эликсир жизни"),
            new Card(CardType.ATTACK, "Ледяная стрела"),
            new Card(CardType.DEFENSE, "Каменная кожа")
        };

        // Добавляем 5 случайных карт
        Random random = new Random();
        for (int i = 0; i < 5; i++) {
            Card card = testCards[random.nextInt(testCards.length)];
            playerHand.add(card);

            // Создаем визуальные элементы карт
            Pane cardPane = createCardPane(card);
            playerCardsContainer.getChildren().add(cardPane);
            cardPanes.add(cardPane);
        }

        // Обновляем доступность карт
        setCardsEnabled(isMyTurn);
    }

    private Pane createCardPane(Card card) {
        // Создаем панель для карты
        Pane pane = new Pane();
        pane.setPrefSize(100, 150);
        pane.getStyleClass().add("card-pane");

        if (!isMyTurn) {
            pane.getStyleClass().add("disabled");
            pane.setDisable(true);
        }

        // Определяем цвет карты по типу
        Color cardColor;
        String cardDescription = "";

        switch (card.getType()) {
            case ATTACK:
                cardColor = Color.rgb(231, 76, 60); // Красный
                cardDescription = "Наносит 2 урона";
                break;
            case DEFENSE:
                cardColor = Color.rgb(52, 152, 219); // Синий
                cardDescription = "Даёт +1 щит";
                break;
            case HEAL:
                cardColor = Color.rgb(46, 204, 113); // Зеленый
                cardDescription = "Восстанавливает 1 HP";
                break;
            default:
                cardColor = Color.GRAY;
                cardDescription = "";
        }

        // Создаем Canvas для рисования карты
        Canvas canvas = new Canvas(100, 150);
        GraphicsContext gc = canvas.getGraphicsContext2D();

        // Рисуем фон карты с градиентом
        gc.setFill(cardColor);
        gc.fillRoundRect(2, 2, 96, 146, 15, 15);

        // Добавляем темный градиент сверху
        gc.setFill(Color.rgb(0, 0, 0, 0.3));
        gc.fillRoundRect(2, 2, 96, 50, 15, 15);

        // Рисуем рамку
        gc.setStroke(Color.WHITE);
        gc.setLineWidth(2);
        gc.strokeRoundRect(2, 2, 96, 146, 15, 15);

        // Рисуем символ типа карты
        gc.setFill(Color.WHITE);
        gc.setTextAlign(TextAlignment.CENTER);
        gc.setFont(javafx.scene.text.Font.font("Arial", 24));

        String symbol = "";
        switch (card.getType()) {
            case ATTACK: symbol = "⚔"; break;
            case DEFENSE: symbol = "🛡"; break;
            case HEAL: symbol = "❤"; break;
        }
        gc.fillText(symbol, 50, 40);

        // Рисуем название карты
        gc.setFont(javafx.scene.text.Font.font("Arial", 11));
        gc.fillText(card.getName(), 50, 80);

        // Рисуем тип карты и описание
        gc.setFont(javafx.scene.text.Font.font("Arial", 9));
        String typeText = card.getType().toString();
        gc.fillText(typeText, 50, 100);

        // Рисуем описание эффекта
        gc.setFont(javafx.scene.text.Font.font("Arial", 8));
        gc.fillText(cardDescription, 50, 115);

        // Рисуем стоимость/силу карты
        gc.setFill(Color.YELLOW);
        gc.setFont(javafx.scene.text.Font.font("Arial", 10));
        switch (card.getType()) {
            case ATTACK: gc.fillText("⚔ 2", 50, 135); break;
            case DEFENSE: gc.fillText("🛡 1", 50, 135); break;
            case HEAL: gc.fillText("❤ 1", 50, 135); break;
        }

        pane.getChildren().add(canvas);

        // Добавляем обработчик клика
        pane.setOnMouseClicked(event -> {
            if (isMyTurn && !pane.isDisabled()) {
                playCard(card);
            } else {
                showMessage("⏳ Сейчас не ваш ход! Ожидайте...");
            }
        });

        // Эффект при наведении
        pane.setOnMouseEntered(event -> {
            if (isMyTurn && !pane.isDisabled()) {
                pane.setStyle("-fx-effect: dropshadow(gaussian, rgba(243, 156, 18, 0.7), 20, 0, 0, 5); -fx-translate-y: -5;");
            }
        });

        pane.setOnMouseExited(event -> {
            pane.setStyle("-fx-translate-y: 0;");
        });

        return pane;
    }

    private Pane createHiddenCard() {
        // Создаем скрытую карту для противника
        Pane pane = new Pane();
        pane.setPrefSize(100, 150);

        Canvas canvas = new Canvas(100, 150);
        GraphicsContext gc = canvas.getGraphicsContext2D();

        // Рисуем рубашку карты с градиентом
        gc.setFill(Color.rgb(30, 30, 40));
        gc.fillRoundRect(2, 2, 96, 146, 15, 15);

        // Добавляем узор
        gc.setFill(Color.rgb(60, 60, 80));
        for (int i = 0; i < 3; i++) {
            double size = 40 - i * 10;
            gc.fillOval(50 - size/2, 75 - size/2, size, size);
        }

        // Рисуем украшения
        gc.setStroke(Color.rgb(100, 100, 120));
        gc.setLineWidth(1);
        gc.strokeLine(20, 30, 80, 120);
        gc.strokeLine(80, 30, 20, 120);
        gc.strokeOval(30, 50, 40, 50);

        // Рисуем текст "?"
        gc.setFill(Color.WHITE);
        gc.setFont(javafx.scene.text.Font.font("Arial", 48));
        gc.setTextAlign(TextAlignment.CENTER);
        gc.fillText("?", 50, 95);

        pane.getChildren().add(canvas);
        pane.getStyleClass().add("card-pane");
        pane.setStyle("-fx-opacity: 0.7;");

        return pane;
    }

    private void playCard(Card card) {
        if (!isMyTurn) {
            showMessage("⏳ Сейчас не ваш ход! Ждите...");
            return;
        }

        System.out.println("🎴 Играем карту: " + card.getName());

        // Показываем анимацию
        showCardAnimation(card);

        // Применяем эффект карты
        applyCardEffect(card);

        // Блокируем карты
        setCardsEnabled(false);
        lastActionLabel.setText("Вы сыграли: " + card.getName());

        // Добавляем сообщение в чат
        String actionMessage = getActionMessage(card);
        addChatMessage("🎯 Вы", actionMessage);

        // Имитация хода противника
        simulateOpponentTurn();
    }

    private void applyCardEffect(Card card) {
        switch (card.getType()) {
            case ATTACK:
                // Наносим урон противнику
                int damage = 2;
                if (opponentShield > 0) {
                    opponentShield -= damage;
                    if (opponentShield < 0) {
                        opponentHP += opponentShield; // отрицательный щит = урон HP
                        opponentShield = 0;
                    }
                } else {
                    opponentHP = Math.max(0, opponentHP - damage);
                }
                break;

            case DEFENSE:
                // Добавляем щит
                playerShield += 1;
                break;

            case HEAL:
                // Лечим себя
                playerHP = Math.min(10, playerHP + 1);
                break;
        }

        // Обновляем отображение
        updateHealthDisplay();

        // Проверяем победу
        checkWinCondition();
    }

    private String getActionMessage(Card card) {
        switch (card.getType()) {
            case ATTACK:
                return "атакует на 2 урона! " + (opponentShield > 0 ? "Щит противника уменьшен" : "Прямое попадание!");
            case DEFENSE:
                return "устанавливает защиту (+1 щит)";
            case HEAL:
                return "восстанавливает 1 HP";
            default:
                return "использует " + card.getName();
        }
    }

    private void simulateOpponentTurn() {
        // Через 2 секунды - ход противника
        PauseTransition pause = new PauseTransition(Duration.seconds(2));
        pause.setOnFinished(e -> {
            isMyTurn = false;
            updateTurnIndicator();

            // Противник делает ход
            opponentMakesMove();

            // Через 2 секунды - снова наш ход
            PauseTransition opponentPause = new PauseTransition(Duration.seconds(2));
            opponentPause.setOnFinished(e2 -> {
                isMyTurn = true;
                updateTurnIndicator();
                setCardsEnabled(true);

                // Обновляем карты (имитация получения новой карты)
                addTestCards();

                addChatMessage("⚔ Система", "Ваш ход! Вы получили новую карту.");
            });
            opponentPause.play();
        });
        pause.play();
    }

    private void opponentMakesMove() {
        // Противник случайно выбирает действие
        Random random = new Random();
        int action = random.nextInt(3);

        String opponentAction = "";
        String chatMessage = "";

        switch (action) {
            case 0: // Атака
                int damage = 2;
                if (playerShield > 0) {
                    playerShield -= damage;
                    if (playerShield < 0) {
                        playerHP += playerShield;
                        playerShield = 0;
                    }
                    chatMessage = "Противник атакует! Ваш щит поглощает урон.";
                } else {
                    playerHP = Math.max(0, playerHP - damage);
                    chatMessage = "Противник атакует! Вы получаете 2 урона.";
                }
                opponentAction = "атакует";
                showOpponentCardAnimation(new Card(CardType.ATTACK, "Темный удар"));
                break;

            case 1: // Защита
                opponentShield += 1;
                chatMessage = "Противник усиливает защиту (+1 щит).";
                opponentAction = "защищается";
                showOpponentCardAnimation(new Card(CardType.DEFENSE, "Теневой щит"));
                break;

            case 2: // Лечение
                opponentHP = Math.min(10, opponentHP + 1);
                chatMessage = "Противник лечится (+1 HP).";
                opponentAction = "лечится";
                showOpponentCardAnimation(new Card(CardType.HEAL, "Темное зелье"));
                break;
        }

        // Обновляем отображение
        updateHealthDisplay();
        lastActionLabel.setText("Противник " + opponentAction);
        addChatMessage("👹 Противник", chatMessage);

        // Проверяем победу
        checkWinCondition();
    }

    private void showCardAnimation(Card card) {
        GraphicsContext gc = battleAnimationCanvas.getGraphicsContext2D();
        battleAnimationCanvas.setVisible(true);
        battleAnimationCanvas.setOpacity(1);

        // Очищаем canvas
        gc.clearRect(0, 0, battleAnimationCanvas.getWidth(), battleAnimationCanvas.getHeight());

        // Определяем цвет и текст анимации
        Color animationColor;
        String animationText = "";
        String effectText = "";

        switch (card.getType()) {
            case ATTACK:
                animationColor = Color.rgb(231, 76, 60, 0.8);
                animationText = "⚔ АТАКА! ⚔";
                effectText = "2 УРОНА";
                break;
            case DEFENSE:
                animationColor = Color.rgb(52, 152, 219, 0.8);
                animationText = "🛡 ЗАЩИТА 🛡";
                effectText = "+1 ЩИТ";
                break;
            case HEAL:
                animationColor = Color.rgb(46, 204, 113, 0.8);
                animationText = "❤ ЛЕЧЕНИЕ ❤";
                effectText = "+1 HP";
                break;
            default:
                animationColor = Color.GRAY;
                animationText = "ДЕЙСТВИЕ";
        }

        // Рисуем фоновый эффект
        gc.setFill(animationColor);
        gc.fillRect(0, 0, battleAnimationCanvas.getWidth(), battleAnimationCanvas.getHeight());

        // Рисуем текст
        gc.setFill(Color.WHITE);
        gc.setFont(javafx.scene.text.Font.font("Arial", 28));
        gc.setTextAlign(TextAlignment.CENTER);
        gc.fillText(animationText, 200, 50);

        gc.setFont(javafx.scene.text.Font.font("Arial", 20));
        gc.fillText(effectText, 200, 80);

        // Анимация исчезновения
        FadeTransition fadeOut = new FadeTransition(Duration.seconds(1.5), battleAnimationCanvas);
        fadeOut.setFromValue(1);
        fadeOut.setToValue(0);
        fadeOut.setOnFinished(e -> battleAnimationCanvas.setVisible(false));
        fadeOut.play();
    }

    private void showOpponentCardAnimation(Card card) {
        lastActionLabel.setText("Противник сыграл: " + card.getName());
        showCardAnimation(card);
    }

    private void updateHealthDisplay() {
        // Обновляем метки
        playerHealthLabel.setText("❤ HP: " + playerHP);
        playerShieldLabel.setText("🛡 Щиты: " + playerShield);
        opponentHealthLabel.setText("❤ HP: " + opponentHP);
        opponentShieldLabel.setText("🛡 Щиты: " + opponentShield);

        // Обновляем полоски здоровья
        updateHealthBars();
    }

    private void updateHealthBars() {
        GraphicsContext playerGc = playerHealthCanvas.getGraphicsContext2D();
        GraphicsContext opponentGc = opponentHealthCanvas.getGraphicsContext2D();

        double width = 150;
        double height = 20;

        // Очищаем canvas
        playerGc.clearRect(0, 0, width, height);
        opponentGc.clearRect(0, 0, width, height);

        // Рисуем полоски здоровья игрока
        drawHealthBar(playerGc, playerHP, playerShield, false);

        // Рисуем полоски здоровья противника
        drawHealthBar(opponentGc, opponentHP, opponentShield, true);
    }

    private void drawHealthBar(GraphicsContext gc, int health, int shield, boolean isOpponent) {
        double width = 150;
        double height = 20;

        // Рисуем фон (максимальное здоровье)
        gc.setFill(Color.rgb(50, 50, 50));
        gc.fillRect(0, 0, width, height);

        // Рисуем текущее здоровье
        double healthWidth = (health / 10.0) * width;
        gc.setFill(Color.rgb(46, 204, 113)); // Зеленый
        gc.fillRect(0, 0, healthWidth, height);

        // Рисуем щиты поверх здоровья
        if (shield > 0) {
            double shieldWidth = Math.min(shield, 10) / 10.0 * width;
            gc.setFill(Color.rgb(52, 152, 219, 0.7)); // Синий с прозрачностью
            gc.fillRect(0, 0, shieldWidth, height);

            // Рисуем текст щитов
            gc.setFill(Color.WHITE);
            gc.setFont(javafx.scene.text.Font.font("Arial", 10));
            if (isOpponent) {
                gc.setTextAlign(TextAlignment.RIGHT);
                gc.fillText("🛡" + shield, width - 3, 14);
            } else {
                gc.setTextAlign(TextAlignment.LEFT);
                gc.fillText("🛡" + shield, 3, 14);
            }
        }

        // Рисуем рамку
        gc.setStroke(Color.WHITE);
        gc.setLineWidth(1);
        gc.strokeRect(0, 0, width, height);

        // Рисуем текст HP
        gc.setFill(Color.WHITE);
        gc.setFont(javafx.scene.text.Font.font("Arial", 10));

        if (isOpponent) {
            gc.setTextAlign(TextAlignment.RIGHT);
            gc.fillText("❤" + health, width - (shield > 0 ? 25 : 5), 14);
        } else {
            gc.setTextAlign(TextAlignment.LEFT);
            gc.fillText("❤" + health, (shield > 0 ? 25 : 5), 14);
        }
    }

    private void updateTurnIndicator() {
        if (isMyTurn) {
            gameStatusLabel.setText("🎯 ВАШ ХОД");
            gameStatusLabel.setStyle("-fx-text-fill: #4CAF50; -fx-font-weight: bold;");
            turnIndicator.setText("Выберите карту для атаки, защиты или лечения");
            turnIndicator.setStyle("-fx-text-fill: #FF9800;");
        } else {
            gameStatusLabel.setText("⏳ ХОД ПРОТИВНИКА");
            gameStatusLabel.setStyle("-fx-text-fill: #FF5722; -fx-font-weight: bold;");
            turnIndicator.setText("Ожидание хода противника...");
            turnIndicator.setStyle("-fx-text-fill: #9E9E9E;");
        }
    }

    private void setCardsEnabled(boolean enabled) {
        for (Pane cardPane : cardPanes) {
            if (enabled) {
                cardPane.getStyleClass().remove("disabled");
                cardPane.setDisable(false);
            } else {
                if (!cardPane.getStyleClass().contains("disabled")) {
                    cardPane.getStyleClass().add("disabled");
                }
                cardPane.setDisable(true);
            }
        }
    }

    private void checkWinCondition() {
        if (opponentHP <= 0) {
            showVictory();
        } else if (playerHP <= 0) {
            showDefeat();
        }
    }

    private void showVictory() {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("ПОБЕДА!");
            alert.setHeaderText("🎉 ВЫ ПОБЕДИЛИ! 🎉");
            alert.setContentText("Противник повержен! Слава герою!\n\nВаше здоровье: " + playerHP + "\nЩиты: " + playerShield);
            alert.showAndWait();

            addChatMessage("🏆 СИСТЕМА", "ВЫ ПОБЕДИЛИ! Противник повержен!");
            gameStatusLabel.setText("🏆 ПОБЕДА!");
            setCardsEnabled(false);
        });
    }

    private void showDefeat() {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle("ПОРАЖЕНИЕ");
            alert.setHeaderText("💀 ВЫ ПРОИГРАЛИ 💀");
            alert.setContentText("Ваш герой пал в бою...\n\nЗдоровье противника: " + opponentHP);
            alert.showAndWait();

            addChatMessage("💀 СИСТЕМА", "ВЫ ПРОИГРАЛИ... Ваш герой пал в бою.");
            gameStatusLabel.setText("💀 ПОРАЖЕНИЕ");
            setCardsEnabled(false);
        });
    }

    @FXML
    private void sendGameChatMessage() {
        String message = gameMessageField.getText().trim();
        if (!message.isEmpty()) {
            if (client != null && client.isConnected()) {
                client.sendChatMessage(message);
                addChatMessage("💬 Вы", message);
                gameMessageField.clear();
            } else {
                // Локальный чат для теста
                addChatMessage("💬 Вы", message);
                gameMessageField.clear();

                // Имитация ответа противника
                if (message.toLowerCase().contains("привет")) {
                    PauseTransition pause = new PauseTransition(Duration.seconds(1));
                    pause.setOnFinished(e -> addChatMessage("💬 Противник", "Привет! Готов к битве?"));
                    pause.play();
                }
            }
        }
    }

    @FXML
    private void surrender() {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Сдаться");
        alert.setHeaderText("Вы уверены, что хотите сдаться?");
        alert.setContentText("Это приведет к немедленному поражению.");

        if (alert.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
            addChatMessage("⚐ СИСТЕМА", "Вы сдались. Поражение!");
            showDefeat();
        }
    }

    @FXML
    private void showRules() {
        String rules = """
            🎮 ПРАВИЛА DUNGEON MAYHEM 🎮

            📊 ОСНОВНОЕ:
            • У каждого игрока 10 HP
            • Победа при снижении HP противника до 0

            🃏 КАРТЫ (3 типа):
            ⚔ АТАКА - Наносит 2 урона противнику
            🛡 ЗАЩИТА - Дает +1 щит (блокирует урон)
            ❤ ЛЕЧЕНИЕ - Восстанавливает 1 HP

            🛡 МЕХАНИКА ЩИТОВ:
            • Щиты блокируют урон прежде здоровья
            • 1 щит = 1 единица урона
            • Щиты не накапливаются сверх 10

            🔄 ХОДЫ:
            • Игроки ходят по очереди
            • После хода получают новую карту
            • Максимум 5 карт в руке

            🎯 СТРАТЕГИЯ:
            • Балансируйте между атакой и защитой
            • Лечитесь, когда HP низкое
            • Следите за щитами противника

            Удачи в битве! ⚔
            """;

        TextArea textArea = new TextArea(rules);
        textArea.setEditable(false);
        textArea.setWrapText(true);
        textArea.setMaxWidth(400);
        textArea.setMaxHeight(300);

        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Правила игры");
        alert.setHeaderText("Dungeon Mayhem - Руководство");
        alert.getDialogPane().setContent(textArea);
        alert.setResizable(true);
        alert.getDialogPane().setPrefSize(420, 350);
        alert.showAndWait();
    }

    @FXML
    private void returnToMenu() {
        try {
            // Восстанавливаем обработчик сообщений
            if (client != null && originalMessageHandler != null) {
                client.messageHandler = originalMessageHandler;
            }

            // Закрываем игровое окно
            Stage currentStage = (Stage) gameChatArea.getScene().getWindow();
            currentStage.close();

            // Открываем главное меню
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main_menu.fxml"));
            Parent root = loader.load();

            Stage menuStage = new Stage();
            menuStage.setTitle("Dungeon Mayhem - Главное меню");
            menuStage.setScene(new Scene(root, 800, 600));
            menuStage.show();

        } catch (Exception e) {
            e.printStackTrace();
            // Если ошибка, просто закрываем окно
            Stage stage = (Stage) gameChatArea.getScene().getWindow();
            stage.close();
        }
    }

    private void addChatMessage(String sender, String message) {
        String time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"));
        String formattedMessage = String.format("[%s] %s: %s\n", time, sender, message);

        Platform.runLater(() -> {
            gameChatArea.appendText(formattedMessage);
            gameChatArea.setScrollTop(Double.MAX_VALUE);
        });
    }

    private void showMessage(String message) {
        Platform.runLater(() -> {
            lastActionLabel.setText(message);
            lastActionLabel.setStyle("-fx-text-fill: #FF9800;");

            // Через 3 секунды очищаем
            PauseTransition pause = new PauseTransition(Duration.seconds(3));
            pause.setOnFinished(e -> lastActionLabel.setText(""));
            pause.play();
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

    // Обработчик сетевых сообщений
    private void handleNetworkMessage(Object message) {
        System.out.println("[GAME] Сетевое сообщение: " + message);

        Platform.runLater(() -> {
            if (message instanceof String) {
                String msg = (String) message;
                if (msg.startsWith("CONNECTED:")) {
                    addChatMessage("🔗 Система", msg.substring(10));
                    gameStatusLabel.setText("Подключено к игре");
                } else if (msg.startsWith("DISCONNECTED:")) {
                    addChatMessage("🔌 Система", "Соединение разорвано: " + msg.substring(13));
                    showError("Потеряно соединение с сервером!");
                } else if (msg.startsWith("ERROR:")) {
                    showError("Ошибка сети: " + msg.substring(6));
                }
            } else if (message instanceof NetworkMessage) {
                NetworkMessage netMsg = (NetworkMessage) message;
                handleNetworkMessageType(netMsg);
            }
        });
    }

    private void handleNetworkMessageType(NetworkMessage message) {
        try {
            switch (message.getType()) {
                case GAME_UPDATE:
                    GameState gameState = (GameState) message.getData();
                    System.out.println("[GAME] Обновление состояния игры");
                    // Здесь будет обновление от сервера
                    break;

                case CHAT_MESSAGE:
                    String chatMsg = (String) message.getData();
                    addChatMessage("💬 Игрок", chatMsg);
                    break;

                case CARD_PLAYED:
                    Card playedCard = (Card) message.getData();
                    System.out.println("[GAME] Противник сыграл карту: " + playedCard.getName());
                    addChatMessage("🎴 Противник", "сыграл карту: " + playedCard.getName());
                    showOpponentCardAnimation(playedCard);
                    break;

                case PLAYER_JOIN:
                    String joinMsg = (String) message.getData();
                    addChatMessage("👥 Система", joinMsg);
                    break;
            }
        } catch (Exception e) {
            System.err.println("[GAME] Ошибка обработки сетевого сообщения: " + e.getMessage());
        }
    }

    public void cleanup() {
        // Восстанавливаем оригинальный обработчик сообщений
        if (client != null && originalMessageHandler != null) {
            client.messageHandler = originalMessageHandler;
        }
    }
}
