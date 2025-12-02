package com.example.dungeon.ui;

import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.scene.paint.CycleMethod;
import javafx.scene.shape.Rectangle;
import javafx.animation.TranslateTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.ParallelTransition;
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
    private boolean isMyTurn = true;
    private String playerName = "Вы";
    private String opponentName = "Противник";
    private int playerId = 0;
    private boolean isNetworkGame = false;
    private boolean waitingForServer = false;

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

    // Таймеры
    private Timeline opponentTurnTimer;

    public GameController(Client client) {
        this.client = client;
        if (client != null) {
            this.originalMessageHandler = client.messageHandler;
            this.isNetworkGame = true;
        }
    }

    @FXML
    public void initialize() {
        System.out.println("🎮 GameController инициализирован");
        System.out.println("📡 Режим игры: " + (isNetworkGame ? "СЕТЕВОЙ" : "ОДИНОЧНЫЙ"));

        // Устанавливаем свой обработчик сообщений
        if (client != null && client.messageHandler != null) {
            this.originalMessageHandler = client.messageHandler;
            client.messageHandler = this::handleNetworkMessage;
        }

        // Инициализируем интерфейс
        initializeUI();

        // Запускаем соответствующую игру
        if (isNetworkGame && client != null && client.isConnected()) {
            startNetworkGame();
        } else {
            startSinglePlayerGame();
        }
    }

    private void initializeUI() {
        playerCardsContainer.getChildren().clear();
        opponentCardsContainer.getChildren().clear();

        updateHealthDisplay();
        gameStatusLabel.setText("🎯 ПОДГОТОВКА К БИТВЕ");
        turnIndicator.setText("Определяем очередность...");

        gameChatArea.setWrapText(true);
        gameChatArea.setEditable(false);

        // Создаем скрытые карты противника
        for (int i = 0; i < 5; i++) {
            Pane hiddenCard = createHiddenCard(i);
            opponentCardsContainer.getChildren().add(hiddenCard);
        }
    }

    private void startSinglePlayerGame() {
        System.out.println("🏁 Запуск одиночной игры");

        addChatMessage("⚔ Система", "ОДИНОЧНАЯ ИГРА - Битва началась!");
        addChatMessage("⚔ Система", "Ваш ход! Выберите карту для атаки, защиты или лечения.");

        // Создаем начальные карты
        createInitialCards();

        // Обновляем статус
        updateTurnIndicator();
        updateHealthDisplay();

        // Делаем карты активными
        setCardsEnabled(true);
    }

    private void startNetworkGame() {
        System.out.println("🏁 Запуск сетевой игры");

        addChatMessage("🔗 Система", "СЕТЕВАЯ ИГРА - Ожидание сервера...");
        addChatMessage("🔗 Система", "Подключено к серверу. Ожидайте начала игры.");

        gameStatusLabel.setText("⏳ ОЖИДАНИЕ СЕРВЕРА");
        turnIndicator.setText("Сервер определяет очередность ходов...");

        waitingForServer = true;

        // Делаем карты неактивными до получения состояния от сервера
        setCardsEnabled(false);
    }

    private void createInitialCards() {
        playerHand.clear();
        cardPanes.clear();
        playerCardsContainer.getChildren().clear();

        // Создаем предопределенный набор карт для баланса
        Card[] initialCards = {
            new Card(CardType.ATTACK, "Огненный шар"),
            new Card(CardType.DEFENSE, "Железный щит"),
            new Card(CardType.HEAL, "Целебное зелье"),
            new Card(CardType.ATTACK, "Удар кинжалом"),
            new Card(CardType.DEFENSE, "Магический барьер")
        };

        for (int i = 0; i < initialCards.length; i++) {
            Card card = initialCards[i];
            playerHand.add(card);

            Pane cardPane = createCardPane(card, i);
            playerCardsContainer.getChildren().add(cardPane);
            cardPanes.add(cardPane);
        }

        updateCardVisualState();
    }

    private Pane createCardPane(Card card, int index) {
        Pane pane = new Pane();
        pane.setPrefSize(100, 150);
        pane.getStyleClass().add("card-pane");
        pane.setId("card-" + index);

        // Анимация при появлении
        pane.setOpacity(0);
        TranslateTransition slideIn = new TranslateTransition(Duration.millis(300), pane);
        slideIn.setFromY(50);
        slideIn.setToY(0);

        FadeTransition fadeIn = new FadeTransition(Duration.millis(300), pane);
        fadeIn.setFromValue(0);
        fadeIn.setToValue(1);

        ParallelTransition parallelTransition = new ParallelTransition(slideIn, fadeIn);
        parallelTransition.setDelay(Duration.millis(index * 100));
        parallelTransition.play();

        // Определяем цвет карты по типу
        Color cardColor;
        String cardDescription = "";

        switch (card.getType()) {
            case ATTACK:
                cardColor = Color.rgb(231, 76, 60);
                cardDescription = "Наносит 2 урона";
                break;
            case DEFENSE:
                cardColor = Color.rgb(52, 152, 219);
                cardDescription = "Даёт +1 щит";
                break;
            case HEAL:
                cardColor = Color.rgb(46, 204, 113);
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
        gc.setStroke(isMyTurn ? Color.WHITE : Color.GRAY);
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

        // Рисуем тип карты
        gc.setFont(javafx.scene.text.Font.font("Arial", 9));
        String typeText = "";
        switch (card.getType()) {
            case ATTACK: typeText = "АТАКА"; break;
            case DEFENSE: typeText = "ЗАЩИТА"; break;
            case HEAL: typeText = "ЛЕЧЕНИЕ"; break;
        }
        gc.fillText(typeText, 50, 100);

        // Рисуем описание эффекта
        gc.setFont(javafx.scene.text.Font.font("Arial", 8));
        gc.fillText(cardDescription, 50, 115);

        // Рисуем стоимость/силу карты
        gc.setFill(Color.YELLOW);
        gc.setFont(javafx.scene.text.Font.font("Arial", 10));
        switch (card.getType()) {
            case ATTACK:
                gc.fillText("⚔ 2", 50, 135);
                break;
            case DEFENSE:
                gc.fillText("🛡 1", 50, 135);
                break;
            case HEAL:
                gc.fillText("❤ 1", 50, 135);
                break;
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

    private Pane createHiddenCard(int index) {
        Pane pane = new Pane();
        pane.setPrefSize(100, 150);

        // Анимация появления
        pane.setOpacity(0);
        TranslateTransition slideIn = new TranslateTransition(Duration.millis(300), pane);
        slideIn.setFromY(-50);
        slideIn.setToY(0);

        FadeTransition fadeIn = new FadeTransition(Duration.millis(300), pane);
        fadeIn.setFromValue(0);
        fadeIn.setToValue(0.7);

        ParallelTransition parallelTransition = new ParallelTransition(slideIn, fadeIn);
        parallelTransition.setDelay(Duration.millis(index * 100));
        parallelTransition.play();

        Canvas canvas = new Canvas(100, 150);
        GraphicsContext gc = canvas.getGraphicsContext2D();

        LinearGradient gradient = new LinearGradient(0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
            new Stop(0, Color.rgb(30, 30, 40)),
            new Stop(1, Color.rgb(50, 50, 70))
        );

        gc.setFill(gradient);
        gc.fillRoundRect(2, 2, 96, 146, 15, 15);

        // Добавляем узор
        gc.setFill(Color.rgb(60, 60, 80, 0.5));
        for (int i = 0; i < 3; i++) {
            double size = 40 - i * 10;
            gc.fillOval(50 - size/2, 75 - size/2, size, size);
        }

        // Рисуем украшения
        gc.setStroke(Color.rgb(100, 100, 120, 0.7));
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

        // Добавляем подсчет карт
        Label countLabel = new Label("?");
        countLabel.setStyle("-fx-text-fill: white; -fx-font-size: 12; -fx-font-weight: bold;");
        countLabel.setLayoutX(85);
        countLabel.setLayoutY(5);
        pane.getChildren().add(countLabel);

        return pane;
    }

    private void playCard(Card card) {
        if (!isMyTurn) {
            showMessage("⏳ Сейчас не ваш ход! Ждите...");
            return;
        }

        System.out.println("🎴 Играем карту: " + card.getName());

        // Удаляем карту из руки
        boolean removed = playerHand.removeIf(c ->
            c.getName().equals(card.getName()) && c.getType() == card.getType());

        if (!removed) {
            showMessage("Ошибка: карта не найдена в руке!");
            return;
        }

        // Обновляем отображение карт
        updateCardDisplay();

        // Отправляем карту через сеть, если это сетевая игра
        if (isNetworkGame && client != null && client.isConnected()) {
            client.playCard(card);
            addChatMessage("🎯 Вы", "сыграл карту: " + card.getName());
        } else {
            // Одиночная игра - применяем эффект сразу
            applyCardEffect(card);
            addChatMessage("🎯 Вы", getActionMessage(card));
        }

        // Показываем анимацию
        showCardAnimation(card);

        // Блокируем карты
        setCardsEnabled(false);
        lastActionLabel.setText("Вы сыграли: " + card.getName());

        // В одиночной игре запускаем ход противника
        if (!isNetworkGame) {
            startOpponentTurn();
        }
    }

    private void applyCardEffect(Card card) {
        int damage = 0;

        switch (card.getType()) {
            case ATTACK:
                damage = 2;
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
                playerShield = Math.min(10, playerShield + 1);
                break;

            case HEAL:
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

    private void startOpponentTurn() {
        isMyTurn = false;
        updateTurnIndicator();

        addChatMessage("⏳ Система", "Ход противника...");

        // Ждем 2 секунды, затем противник делает ход
        PauseTransition pause = new PauseTransition(Duration.seconds(2));
        pause.setOnFinished(e -> {
            opponentMakesMove();

            // Через 2 секунды возвращаем ход
            PauseTransition returnPause = new PauseTransition(Duration.seconds(2));
            returnPause.setOnFinished(e2 -> {
                endOpponentTurn();
            });
            returnPause.play();
        });
        pause.play();
    }

    private void opponentMakesMove() {
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
                        chatMessage = "Противник атакует! Пробит щит и нанесен урон.";
                    } else {
                        chatMessage = "Противник атакует! Ваш щит поглощает урон.";
                    }
                } else {
                    playerHP = Math.max(0, playerHP - damage);
                    chatMessage = "Противник атакует! Вы получаете 2 урона.";
                }
                opponentAction = "атакует";
                showOpponentCardAnimation(new Card(CardType.ATTACK, "Темный удар"));
                break;

            case 1: // Защита
                opponentShield = Math.min(10, opponentShield + 1);
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

    private void endOpponentTurn() {
        isMyTurn = true;
        updateTurnIndicator();

        // Добавляем новую карту
        addRandomCardToHand();

        // Разблокируем карты
        setCardsEnabled(true);

        addChatMessage("⚔ Система", "Ваш ход! Вы получили новую карту.");
    }

    private void addRandomCardToHand() {
        if (playerHand.size() >= 5) return; // Максимум 5 карт в руке

        Card[] possibleCards = {
            new Card(CardType.ATTACK, "Огненный шар"),
            new Card(CardType.DEFENSE, "Железный щит"),
            new Card(CardType.HEAL, "Целебное зелье"),
            new Card(CardType.ATTACK, "Удар кинжалом"),
            new Card(CardType.DEFENSE, "Магический барьер"),
            new Card(CardType.HEAL, "Эликсир жизни"),
            new Card(CardType.ATTACK, "Ледяная стрела"),
            new Card(CardType.DEFENSE, "Каменная кожа")
        };

        Random random = new Random();
        Card newCard = possibleCards[random.nextInt(possibleCards.length)];
        playerHand.add(newCard);

        // Обновляем отображение карт
        updateCardDisplay();
    }

    private void showCardAnimation(Card card) {
        GraphicsContext gc = battleAnimationCanvas.getGraphicsContext2D();
        battleAnimationCanvas.setVisible(true);
        battleAnimationCanvas.setOpacity(1);

        // Очищаем canvas
        gc.clearRect(0, 0, battleAnimationCanvas.getWidth(), battleAnimationCanvas.getHeight());

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
        playerHealthLabel.setText("❤ HP: " + playerHP);
        playerShieldLabel.setText("🛡 Щиты: " + playerShield);
        opponentHealthLabel.setText("❤ HP: " + opponentHP);
        opponentShieldLabel.setText("🛡 Щиты: " + opponentShield);

        updateHealthBars();
    }

    private void updateHealthBars() {
        GraphicsContext playerGc = playerHealthCanvas.getGraphicsContext2D();
        GraphicsContext opponentGc = opponentHealthCanvas.getGraphicsContext2D();

        double width = 150;
        double height = 20;

        playerGc.clearRect(0, 0, width, height);
        opponentGc.clearRect(0, 0, width, height);

        drawHealthBar(playerGc, playerHP, playerShield, false);
        drawHealthBar(opponentGc, opponentHP, opponentShield, true);
    }

    private void drawHealthBar(GraphicsContext gc, int health, int shield, boolean isOpponent) {
        double width = 150;
        double height = 20;

        // Рисуем фон
        gc.setFill(Color.rgb(50, 50, 50));
        gc.fillRect(0, 0, width, height);

        // Рисуем текущее здоровье
        double healthWidth = (health / 10.0) * width;
        gc.setFill(Color.rgb(46, 204, 113));
        gc.fillRect(0, 0, healthWidth, height);

        // Рисуем щиты поверх здоровья
        if (shield > 0) {
            double shieldWidth = Math.min(shield, 10) / 10.0 * width;
            gc.setFill(Color.rgb(52, 152, 219, 0.7));
            gc.fillRect(0, 0, shieldWidth, height);

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

    private void updateCardDisplay() {
        playerCardsContainer.getChildren().clear();
        cardPanes.clear();

        if (playerHand.isEmpty()) {
            Label noCardsLabel = new Label("Нет карт в руке");
            noCardsLabel.setStyle("-fx-text-fill: #888; -fx-font-size: 14;");
            playerCardsContainer.getChildren().add(noCardsLabel);
            return;
        }

        for (int i = 0; i < playerHand.size(); i++) {
            Card card = playerHand.get(i);
            Pane cardPane = createCardPane(card, i);
            playerCardsContainer.getChildren().add(cardPane);
            cardPanes.add(cardPane);
        }

        updateCardVisualState();
    }

    private void updateCardVisualState() {
        for (Pane cardPane : cardPanes) {
            if (isMyTurn) {
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
        this.isMyTurn = enabled;
        updateCardVisualState();
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
            if (isNetworkGame && client != null && client.isConnected()) {
                client.sendChatMessage(message);
                addChatMessage("💬 Вы", message);
                gameMessageField.clear();
            } else {
                addChatMessage("💬 Вы", message);
                gameMessageField.clear();
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
                case CHAT_MESSAGE:
                    String chatMsg = (String) message.getData();
                    if (!chatMsg.startsWith("Вы:")) {
                        addChatMessage("💬 Игрок", chatMsg);
                    }
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

                case GAME_UPDATE:
                    GameState gameState = (GameState) message.getData();
                    System.out.println("[SERVER] Получено обновление игры");

                    Platform.runLater(() -> {
                        waitingForServer = false;

                        // Определяем, кто мы - игрок 1 или игрок 2
                        String currentPlayerName = gameState.getCurrentPlayer().getName();
                        if (currentPlayerName.contains("1") && playerId == 0) {
                            playerId = 1;
                            addChatMessage("⚔ Система", "Вы - Игрок 1");
                        } else if (currentPlayerName.contains("2") && playerId == 0) {
                            playerId = 2;
                            addChatMessage("⚔ Система", "Вы - Игрок 2");
                        }

                        // Определяем, наш ли это ход
                        boolean isOurTurn = (playerId == 1 && gameState.isPlayerTurn()) ||
                            (playerId == 2 && !gameState.isPlayerTurn());

                        // Обновляем состояние игры
                        updateGameFromServer(gameState, isOurTurn);
                    });
                    break;
            }
        } catch (Exception e) {
            System.err.println("[GAME] Ошибка обработки сетевого сообщения: " + e.getMessage());
        }
    }

    private void updateGameFromServer(GameState gameState, boolean isOurTurn) {
        this.currentGameState = gameState;
        this.isMyTurn = isOurTurn;

        System.out.println("[DEBUG] playerId: " + playerId + ", isOurTurn: " + isOurTurn);

        Player myPlayer, opponent;
        if (playerId == 1) {
            myPlayer = gameState.getCurrentPlayer();
            opponent = gameState.getOpponentPlayer();
        } else if (playerId == 2) {
            myPlayer = gameState.getOpponentPlayer();
            opponent = gameState.getCurrentPlayer();
        } else {
            myPlayer = gameState.getCurrentPlayer();
            opponent = gameState.getOpponentPlayer();
        }

        // Обновляем здоровье
        playerHP = myPlayer.getHealth();
        playerShield = myPlayer.getShield();
        opponentHP = opponent.getHealth();
        opponentShield = opponent.getShield();

        updateHealthDisplay();

        // Обновляем карты в руке
        if (myPlayer.getHand() != null) {
            updatePlayerHandFromServer(myPlayer.getHand());
        }

        updateTurnIndicator();
        setCardsEnabled(isMyTurn);

        if (gameState.getGameStatus() != null) {
            gameStatusLabel.setText(gameState.getGameStatus());
        }

        if (opponent.getHand() != null) {
            updateOpponentCards(opponent.getHand().size());
        }
    }

    private void updatePlayerHandFromServer(List<Card> hand) {
        playerHand.clear();
        playerHand.addAll(hand);
        updateCardDisplay();
    }

    private void updateOpponentCards(int cardCount) {
        opponentCardsContainer.getChildren().clear();

        for (int i = 0; i < cardCount; i++) {
            Pane hiddenCard = createHiddenCard(i);
            opponentCardsContainer.getChildren().add(hiddenCard);
        }
    }

    public void cleanup() {
        if (client != null && originalMessageHandler != null) {
            client.messageHandler = originalMessageHandler;
        }

        if (opponentTurnTimer != null) {
            opponentTurnTimer.stop();
        }
    }
}
