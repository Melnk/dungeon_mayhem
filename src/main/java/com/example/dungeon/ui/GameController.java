package com.example.dungeon.ui;

import com.example.dungeon.game.*;
import com.example.dungeon.network.Client;
import com.example.dungeon.network.GameNetworkController;
import javafx.animation.*;
import javafx.application.Platform;
import javafx.beans.property.*;
import javafx.fxml.FXML;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.*;
import javafx.scene.shape.ArcType;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;
import javafx.stage.Stage;
import javafx.util.Duration;
import lombok.Setter;

import java.time.Instant;
import java.util.List;

public class GameController implements GameEventListener, GameNetworkController.NetworkListener {

    // FXML элементы
    @FXML private Label playerHealthLabel;
    @FXML private Label playerShieldLabel;
    @FXML private Label opponentHealthLabel;
    @FXML private Label opponentShieldLabel;
    @FXML private Label gameStatusLabel;
    @FXML private Label turnIndicator;
    @FXML private Label lastActionLabel;
    @FXML private Label gameTimerLabel;
    @FXML private Label playerCharacterLabel;
    @FXML private Label opponentCharacterLabel;

    @FXML private Canvas playerHealthCanvas;
    @FXML private Canvas opponentHealthCanvas;
    @FXML private Canvas battleAnimationCanvas;
    @FXML private Canvas victoryIndicatorCanvas;

    @FXML private HBox playerCardsContainer;
    @FXML private HBox opponentCardsContainer;

    @FXML private TextArea gameChatArea;
    @FXML private TextField gameMessageField;

    // Индикатор победы элементы
    @FXML private Label victoryPercentageLabel;
    @FXML private Label victoryStatusLabel;
    @FXML private StackPane victoryContainer;

    // Игровые компоненты
    private GameEngine engine;
    private CardViewFactory cardFactory;
    private HealthBarRenderer hbRenderer;
    private AnimationManager animationManager;
    private ChatService chatService;
    private GameNetworkController networkController;
    private Client client;

    // Состояние игры
    private boolean isMyTurn = true;
    private Boolean serverTurnKnown = null;
    private Timeline gameTimer;
    private Instant timerStart;
    private int playerMaxHP = 10;
    private int opponentMaxHP = 10;

    @Setter
    private GameState initialGameState;

    // Текущие данные игроков (для онлайн и офлайн режимов)
    private Player currentPlayer;
    private Player currentOpponent;

    // Индикатор победы состояние
    private GraphicsContext victoryGc;
    private final DoubleProperty victoryPercentage = new SimpleDoubleProperty(50); // 0-100%
    private final DoubleProperty indicatorAngle = new SimpleDoubleProperty(0); // угол стрелки (0-360)
    private final Timeline indicatorAnimation = new Timeline();
    private final Timeline victoryPulseAnimation = new Timeline();

    public GameController() {}

    @FXML
    public void initialize() {
        initializeGameComponents();
        initializeVictoryIndicator();

        if (initialGameState != null) {
            applyInitialGameState(initialGameState);
            initialGameState = null;
        }

        if (client == null) {
            startSinglePlayer();
        } else {
            chatService.addChatMessage("Сеть", "Ожидание состояния от сервера...");
        }
    }

    private void initializeGameComponents() {
        this.engine = new GameEngine();
        this.engine.setListener(this);

        this.cardFactory = new CardViewFactory();
        this.hbRenderer = new HealthBarRenderer();
        this.animationManager = new AnimationManager(battleAnimationCanvas);
        this.chatService = new ChatService(gameChatArea);

        serverTurnKnown = null;

        if (playerCharacterLabel != null && opponentCharacterLabel != null) {
            playerCharacterLabel.setText("🎭 Загрузка...");
            opponentCharacterLabel.setText("🎭 Загрузка...");
        }
    }

    private void initializeVictoryIndicator() {
        if (victoryIndicatorCanvas == null) {
            System.err.println("WARNING: victoryIndicatorCanvas is null!");
            return;
        }

        victoryGc = victoryIndicatorCanvas.getGraphicsContext2D();
        victoryGc.setImageSmoothing(true);

        // Инициализация анимации индикатора
        indicatorAnimation.setCycleCount(Timeline.INDEFINITE);
        indicatorAnimation.getKeyFrames().add(
            new KeyFrame(Duration.millis(16), e -> redrawVictoryIndicator())
        );
        indicatorAnimation.play();

        // Пульсация при значительном преимуществе
        victoryPulseAnimation.setCycleCount(Timeline.INDEFINITE);
        victoryPulseAnimation.setAutoReverse(true);
        victoryPulseAnimation.getKeyFrames().addAll(
            new KeyFrame(Duration.ZERO,
                new KeyValue(victoryContainer.scaleXProperty(), 1.0),
                new KeyValue(victoryContainer.scaleYProperty(), 1.0)),
            new KeyFrame(Duration.millis(1200),
                new KeyValue(victoryContainer.scaleXProperty(), 1.05),
                new KeyValue(victoryContainer.scaleYProperty(), 1.05))
        );

        // Слушатель изменений процента победы
        victoryPercentage.addListener((obs, oldVal, newVal) -> {
            double newAngle = newVal.doubleValue() * 3.6; // 100% = 360°

            Timeline angleAnimation = new Timeline(
                new KeyFrame(Duration.millis(400),
                    new KeyValue(indicatorAngle, newAngle,
                        Interpolator.EASE_BOTH))
            );
            angleAnimation.play();

            updateVictoryStatus();
        });

        // Начальные значения
        victoryPercentage.set(50.0);
        indicatorAngle.set(180); // Начальное положение стрелки (50%)
    }

    private void redrawVictoryIndicator() {
        double width = victoryIndicatorCanvas.getWidth();
        double height = victoryIndicatorCanvas.getHeight();
        double centerX = width / 2;
        double centerY = height / 2;
        double radius = Math.min(width, height) * 0.35;

        // Очистка
        victoryGc.clearRect(0, 0, width, height);

        // 1. Фоновый круг
        RadialGradient bgGradient = new RadialGradient(
            0, 0, centerX, centerY, radius,
            false, CycleMethod.NO_CYCLE,
            new Stop(0, Color.rgb(40, 40, 42, 0.8)),
            new Stop(1, Color.rgb(20, 20, 22, 0.9))
        );
        victoryGc.setFill(bgGradient);
        victoryGc.fillOval(centerX - radius, centerY - radius, radius * 2, radius * 2);

        // 2. Цветное кольцо (градиент от красного через серый к зеленому)
        double ringWidth = 12;
        double innerRadius = radius - ringWidth/2;

        LinearGradient ringGradient = new LinearGradient(
            0, 0, 1, 0, true, CycleMethod.NO_CYCLE,
            new Stop(0, Color.RED),           // 0% - красный
            new Stop(0.25, Color.ORANGE),     // 25%
            new Stop(0.5, Color.GRAY),        // 50% - серый
            new Stop(0.75, Color.LIMEGREEN),  // 75%
            new Stop(1, Color.LIME)           // 100% - зеленый
        );

        victoryGc.setStroke(ringGradient);
        victoryGc.setLineWidth(ringWidth);
        victoryGc.setLineCap(StrokeLineCap.ROUND);

        // Рисуем полное кольцо (360 градусов)
        victoryGc.strokeArc(
            centerX - innerRadius, centerY - innerRadius,
            innerRadius * 2, innerRadius * 2,
            90, 360, // Полный круг
            ArcType.OPEN
        );

        // 3. Стрелка (показывающая процент)
        victoryGc.save();
        victoryGc.translate(centerX, centerY);
        victoryGc.rotate(indicatorAngle.get());

        // Длина и ширина стрелки
        double arrowLength = innerRadius * 0.8;
        double arrowWidth = arrowLength * 0.2;

        // Цвет стрелки в зависимости от процента
        double percent = victoryPercentage.get() / 100.0;
        Color arrowColor;
        if (percent < 0.33) {
            arrowColor = Color.RED.interpolate(Color.ORANGE, percent * 3);
        } else if (percent < 0.66) {
            arrowColor = Color.ORANGE.interpolate(Color.GRAY, (percent - 0.33) * 3);
        } else {
            arrowColor = Color.GRAY.interpolate(Color.LIME, (percent - 0.66) * 3);
        }

        victoryGc.setFill(arrowColor);

        // Рисуем стрелку (треугольник)
        victoryGc.beginPath();
        victoryGc.moveTo(0, -arrowLength);
        victoryGc.lineTo(-arrowWidth, 0);
        victoryGc.lineTo(arrowWidth, 0);
        victoryGc.closePath();
        victoryGc.fill();

        // Центральный круг
        victoryGc.setFill(Color.rgb(30, 30, 32, 0.9));
        victoryGc.fillOval(-6, -6, 12, 12);

        victoryGc.restore();

        // 4. Деления (0%, 25%, 50%, 75%, 100%)
        victoryGc.setStroke(Color.rgb(255, 255, 255, 0.4));
        victoryGc.setLineWidth(1.5);

        String[] percentages = {"0%", "25%", "50%", "75%", "100%"};
        double[] angles = {90, 0, 270, 180, 90}; // В градусах

        for (int i = 0; i < percentages.length; i++) {
            double angle = Math.toRadians(angles[i]);
            double tickRadius = innerRadius + 8;
            double tickLength = 6;

            double x1 = centerX + Math.cos(angle) * tickRadius;
            double y1 = centerY + Math.sin(angle) * tickRadius;
            double x2 = centerX + Math.cos(angle) * (tickRadius + tickLength);
            double y2 = centerY + Math.sin(angle) * (tickRadius + tickLength);

            victoryGc.strokeLine(x1, y1, x2, y2);

            // Подписи процентов (только по углам)
            double textRadius = tickRadius + 18;
            double textX = centerX + Math.cos(angle) * textRadius;
            double textY = centerY + Math.sin(angle) * textRadius;

            victoryGc.setFill(Color.rgb(255, 255, 255, 0.7));
            victoryGc.setFont(Font.font("Inter", 10));
            victoryGc.setTextAlign(TextAlignment.CENTER);
            victoryGc.fillText(percentages[i], textX, textY);
        }
    }

    private void updateVictoryStatus() {
        double percent = victoryPercentage.get();

        // Убираем дублирование - теперь текст только в лейбле
        victoryPercentageLabel.setText(String.format("%.0f%%", percent));

        if (percent >= 70) {
            victoryStatusLabel.setText("ПРЕИМУЩЕСТВО");
            victoryStatusLabel.setStyle("-fx-text-fill: #4dff88; -fx-font-weight: 900;");

            if (!victoryPulseAnimation.getStatus().equals(Animation.Status.RUNNING)) {
                victoryPulseAnimation.play();
            }
        } else if (percent <= 30) {
            victoryStatusLabel.setText("ОТСТАВАНИЕ");
            victoryStatusLabel.setStyle("-fx-text-fill: #ff6b35; -fx-font-weight: 900;");

            if (!victoryPulseAnimation.getStatus().equals(Animation.Status.RUNNING)) {
                victoryPulseAnimation.play();
            }
        } else {
            victoryStatusLabel.setText("РАВНОВЕСИЕ");
            victoryStatusLabel.setStyle("-fx-text-fill: #a3d5ff; -fx-font-weight: 800;");

            if (victoryPulseAnimation.getStatus().equals(Animation.Status.RUNNING)) {
                victoryPulseAnimation.stop();
                victoryContainer.setScaleX(1.0);
                victoryContainer.setScaleY(1.0);
            }
        }
    }

    /**
     * Формула расчета вероятности победы (работает для онлайн и офлайн режимов)
     * playerScore = (playerHP + playerShield * 0.8) * (1 + playerCardsCount * 0.05)
     * opponentScore = (opponentHP + opponentShield * 0.8) * (1 + opponentCardsCount * 0.05)
     * victoryPercentage = playerScore / (playerScore + opponentScore) * 100
     */
    private void calculateVictoryPercentage() {
        Player player;
        Player opponent;

        if (client == null) {
            // Офлайн режим - берем из engine
            if (engine == null) return;
            player = engine.getPlayer();
            opponent = engine.getOpponent();
        } else {
            // Онлайн режим - берем из сохраненных данных
            player = currentPlayer;
            opponent = currentOpponent;
        }

        if (player == null || opponent == null) {
            // Если данные недоступны, устанавливаем 50%
            victoryPercentage.set(50);
            return;
        }

        final double shieldCoefficient = 0.8;
        final double cardBonus = 0.05;

        // Рассчитываем очки игрока
        int playerHP = Math.max(0, player.getHealth());
        int playerShield = Math.max(0, player.getShield());
        int playerCards = player.getHand() != null ? player.getHand().size() : 0;

        double playerScore = (playerHP + playerShield * shieldCoefficient) *
            (1 + playerCards * cardBonus);

        // Рассчитываем очки противника
        int opponentHP = Math.max(0, opponent.getHealth());
        int opponentShield = Math.max(0, opponent.getShield());
        int opponentCards = opponent.getHand() != null ? opponent.getHand().size() : 0;

        double opponentScore = (opponentHP + opponentShield * shieldCoefficient) *
            (1 + opponentCards * cardBonus);

        // Бонус за максимальное здоровье
        if (playerMaxHP > opponentMaxHP) {
            playerScore *= (1 + (playerMaxHP - opponentMaxHP) * 0.01);
        } else if (opponentMaxHP > playerMaxHP) {
            opponentScore *= (1 + (opponentMaxHP - playerMaxHP) * 0.01);
        }

        // Гарантируем минимальные значения
        playerScore = Math.max(playerScore, 0.1);
        opponentScore = Math.max(opponentScore, 0.1);

        // Рассчитываем процент победы
        double victoryPercent = (playerScore / (playerScore + opponentScore)) * 100;

        // Ограничиваем значения 0-100%
        victoryPercent = Math.max(0, Math.min(100, victoryPercent));

        // Обновляем процент (с плавной анимацией)
        double currentPercent = victoryPercentage.get();
        double diff = victoryPercent - currentPercent;

        if (Math.abs(diff) > 1) {
            Timeline updateAnimation = new Timeline(
                new KeyFrame(Duration.millis(300),
                    new KeyValue(victoryPercentage, victoryPercent,
                        Interpolator.EASE_BOTH))
            );
            updateAnimation.play();
        } else {
            victoryPercentage.set(victoryPercent);
        }
    }

    @Override
    public void onHealthUpdated(int playerHP, int playerShield, int opponentHP, int opponentShield) {
        Platform.runLater(() -> {
            playerHealthLabel.setText(String.format("❤ HP: %d/%d",
                Math.max(0, playerHP), Math.max(1, playerMaxHP)));
            playerShieldLabel.setText("🛡 Щит: " + Math.max(0, playerShield));
            opponentHealthLabel.setText(String.format("❤ HP: %d/%d",
                Math.max(0, opponentHP), Math.max(1, opponentMaxHP)));
            opponentShieldLabel.setText("🛡 Щит: " + Math.max(0, opponentShield));

            hbRenderer.drawHealthBar(playerHealthCanvas.getGraphicsContext2D(),
                playerHP, Math.max(1, playerMaxHP), playerShield, false);
            hbRenderer.drawHealthBar(opponentHealthCanvas.getGraphicsContext2D(),
                opponentHP, Math.max(1, opponentMaxHP), opponentShield, true);

            // Обновляем данные игроков в онлайн режиме
            if (client != null && currentPlayer != null && currentOpponent != null) {
                // Обновляем данные из текущих объектов
                // В реальной реализации нужно было бы обновлять их из внешнего источника
                // Для упрощения пересчитываем на основе входящих данных
                calculateVictoryPercentage();
            } else {
                calculateVictoryPercentage();
            }
        });
    }

    private void updateCharacterInfo(Player player, Player opponent) {
        Platform.runLater(() -> {
            if (player != null && player.getCharacter() != null) {
                playerMaxHP = Math.max(1, player.getCharacter().getBaseHealth());
                playerHealthLabel.setText(String.format("❤ HP: %d/%d",
                    player.getHealth(), playerMaxHP));
                currentPlayer = player; // Сохраняем для онлайн режима
            }

            if (opponent != null && opponent.getCharacter() != null) {
                opponentMaxHP = Math.max(1, opponent.getCharacter().getBaseHealth());
                opponentHealthLabel.setText(String.format("❤ HP: %d/%d",
                    opponent.getHealth(), opponentMaxHP));
                currentOpponent = opponent; // Сохраняем для онлайн режима
            }

            calculateVictoryPercentage();
        });
    }

    @Override
    public void onHandUpdated(List<Card> playerHand) {
        Platform.runLater(() -> {
            playerCardsContainer.getChildren().clear();

            boolean enabledVisual;
            if (client != null) {
                enabledVisual = (serverTurnKnown == null) ? true : isMyTurn;
            } else {
                isMyTurn = engine.isPlayerTurn();
                enabledVisual = isMyTurn;
            }

            for (int i = 0; i < playerHand.size(); i++) {
                Card c = playerHand.get(i);
                boolean finalEnabledVisual = enabledVisual;

                var pane = cardFactory.createCardPane(c, i, finalEnabledVisual, card -> {
                    boolean allowLocalPlay = (client == null && engine != null && engine.isPlayerTurn());
                    boolean allowNetworkSend = client != null && isMyTurn;

                    if (!allowLocalPlay && !allowNetworkSend) {
                        chatService.addChatMessage("Система", "Сейчас не ваш ход!");
                        return;
                    }

                    if (client != null) {
                        if (networkController != null) {
                            networkController.playCard(card);
                            chatService.addChatMessage("Вы", card.getName());
                            serverTurnKnown = null;
                            setPlayerCardsEnabled(false);
                        }
                    } else {
                        engine.playCard(card, false);
                        chatService.addChatMessage("Вы", card.getName());
                    }
                });

                playerCardsContainer.getChildren().add(pane);
            }

            updateTurnVisuals();

            // Обновляем данные игрока в онлайн режиме
            if (client != null && currentPlayer != null) {
                // В реальной реализации нужно обновлять hand у currentPlayer
                // Для упрощения пересчитываем процент
                calculateVictoryPercentage();
            } else {
                calculateVictoryPercentage();
            }
        });
    }

    @Override
    public void onOpponentHandCountUpdated(int count) {
        Platform.runLater(() -> {
            opponentCardsContainer.getChildren().clear();
            for (int i = 0; i < count; i++) {
                opponentCardsContainer.getChildren().add(cardFactory.createHiddenCard(i));
            }

            // Обновляем данные противника в онлайн режиме
            if (client != null && currentOpponent != null) {
                // В реальной реализации нужно обновлять hand у currentOpponent
                calculateVictoryPercentage();
            } else {
                calculateVictoryPercentage();
            }
        });
    }

    @Override
    public void onGameStatusUpdated(String status) {
        Platform.runLater(() -> {
            gameStatusLabel.setText(status);
            if (status != null && status.toUpperCase().contains("ВАШ")) isMyTurn = true;
            else if (status != null && status.toUpperCase().contains("ХОД ПРОТИВНИКА")) isMyTurn = false;
            serverTurnKnown = true;
            updateTurnVisuals();
        });
    }

    @Override
    public void onActionOccurred(String description) {
        Platform.runLater(() -> {
            lastActionLabel.setText(description);
            chatService.addChatMessage("Действие", description);
        });
    }

    @Override
    public void onGameOver(boolean playerWon, int playerHP, int opponentHP) {
        Platform.runLater(() -> {
            stopTimer();
            updateTurnVisualsDisableAll();

            if (playerWon) {
                victoryPercentage.set(100);
            } else {
                victoryPercentage.set(0);
            }

            String title = playerWon ? "🎉 ПОБЕДА!" : "💀 ПОРАЖЕНИЕ";
            String message = playerWon ?
                "Вы победили! Ваши навыки неоспоримы!" :
                "Вы проиграли. В следующий раз повезёт больше!";

            showGameOverDialog(message, playerWon);
            chatService.addChatMessage("Система", playerWon ? "ВЫ ПОБЕДИЛИ!" : "ВЫ ПРОИГРАЛИ.");
        });
    }

    @Override
    public void onCardPlayed(Card card, boolean byOpponent) {
        Platform.runLater(() -> {
            animationManager.showCardAnimation(card);
            chatService.addChatMessage(byOpponent ? "Противник" : "Вы", "сыграл: " + card.getName());
            calculateVictoryPercentage();
        });
    }

    @Override
    public void onYourTurn(boolean isYourTurn) {
        Platform.runLater(() -> {
            this.isMyTurn = isYourTurn;
            this.serverTurnKnown = true;

            if (isYourTurn) {
                gameStatusLabel.setText("🎯 ВАШ ХОД");
                chatService.addChatMessage("Система", "Теперь ваш ход!");
                setPlayerCardsEnabled(true);
            } else {
                gameStatusLabel.setText("⏳ ХОД ПРОТИВНИКА");
                setPlayerCardsEnabled(false);
            }

            updateTurnVisuals();
        });
    }

    @Override
    public void onGameOver(String result) {
        Platform.runLater(() -> {
            stopTimer();
            updateTurnVisualsDisableAll();

            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Игра завершена");
            alert.setHeaderText("Результат игры");
            alert.setContentText(result);

            ButtonType menuButton = new ButtonType("В главное меню");
            alert.getButtonTypes().setAll(menuButton);
            alert.showAndWait();

            chatService.addChatMessage("Система", result);
            returnToMenu();
        });
    }

    @Override
    public void onPlayerInfo(String info) {
        Platform.runLater(() -> {
            chatService.addChatMessage("🎭 Система", info);

            Alert infoAlert = new Alert(Alert.AlertType.INFORMATION);
            infoAlert.setTitle("Информация о персонаже");
            infoAlert.setHeaderText("Ваш персонаж");
            infoAlert.setContentText(info);
            infoAlert.showAndWait();
        });
    }

    @Override
    public void onChatMessage(String sender, String message) {
        chatService.addChatMessage(sender, message);
    }

    @Override
    public void onGameUpdate(GameState state) {
        Platform.runLater(() -> {
            if (state == null) return;
            this.isMyTurn = state.isPlayerTurn();
            this.serverTurnKnown = true;
            applyInitialGameState(state);
            updateTurnVisuals();
        });
    }

    @Override
    public void onCardPlayed(Card card) {
        onCardPlayed(card, true);
    }

    @Override
    public void onConnected(String info) {
        chatService.addChatMessage("Сеть", info);
    }

    @Override
    public void onDisconnected(String reason) {
        chatService.addChatMessage("Сеть", "Отключено: " + reason);
        setPlayerCardsEnabled(false);
        stopTimer();

        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("Соединение потеряно");
        alert.setHeaderText("Соединение с сервером разорвано");
        alert.setContentText("Причина: " + reason + "\nВозврат в главное меню.");
        alert.showAndWait();

        returnToMenu();
    }

    @Override
    public void onError(String error) {
        chatService.addChatMessage("Сеть", "Ошибка: " + error);
    }

    private void applyInitialGameState(GameState state) {
        if (state == null) return;
        Player me = state.getCurrentPlayer();
        Player opp = state.getOpponentPlayer();
        if (me != null && opp != null) {
            // Сохраняем данные игроков для онлайн режима
            currentPlayer = me;
            currentOpponent = opp;

            updateCharacterInfo(me, opp);
            onHealthUpdated(me.getHealth(), me.getShield(), opp.getHealth(), opp.getShield());
            onHandUpdated(me.getHand() == null ? List.of() : me.getHand());
            onOpponentHandCountUpdated(opp.getHand() == null ? 0 : opp.getHand().size());
            isMyTurn = state.isPlayerTurn();
            serverTurnKnown = true;
            if (isMyTurn) onGameStatusUpdated("🎯 ВАШ ХОД");
            else onGameStatusUpdated("⏳ ХОД ПРОТИВНИКА");
            startTimer();

            // Важно: пересчитываем процент ПОСЛЕ того, как все данные обновлены
            calculateVictoryPercentage();
        } else {
            chatService.addChatMessage("Система", "Получено неполное состояние от сервера.");
        }
    }

    public void setClient(Client client) {
        this.client = client;
        if (client != null) {
            this.networkController = new GameNetworkController(client, this);
            serverTurnKnown = null;
        }
    }

    private void startSinglePlayer() {
        engine.startSinglePlayer();
        isMyTurn = engine.isPlayerTurn();
        updateTurnVisuals();
        startTimer();

        // Инициализируем данные игроков для офлайн режима
        currentPlayer = engine.getPlayer();
        currentOpponent = engine.getOpponent();
        calculateVictoryPercentage();
    }

    private void startTimer() {
        stopTimer();
        timerStart = Instant.now();
        gameTimer = new Timeline(new KeyFrame(Duration.seconds(1), e -> updateTimerLabel()));
        gameTimer.setCycleCount(Timeline.INDEFINITE);
        gameTimer.play();
    }

    private void stopTimer() {
        if (gameTimer != null) {
            gameTimer.stop();
            gameTimer = null;
        }
    }

    private void updateTimerLabel() {
        if (timerStart == null) {
            gameTimerLabel.setText("00:00");
            return;
        }
        long elapsed = java.time.Duration.between(timerStart, Instant.now()).getSeconds();
        long mins = elapsed / 60;
        long secs = elapsed % 60;
        gameTimerLabel.setText(String.format("%02d:%02d", mins, secs));
    }

    @FXML
    public void sendGameChatMessage() {
        String msg = gameMessageField.getText().trim();
        if (msg.isEmpty()) return;

        if (client != null) {
            if (networkController != null) {
                networkController.sendChat(msg);
            } else if (client.isConnected()) {
                client.sendChatMessage(msg);
            }
        }

        chatService.addChatMessage("Вы", msg);
        gameMessageField.clear();
    }

    @FXML
    public void showRules() {
        String rules = """
            ⚔️ ПРАВИЛА DUNGEON MAYHEM ⚔️

            📋 ЦЕЛЬ ИГРЫ:
            • Победить всех противников
            • Последний выживший игрок побеждает

            🎯 ИГРОВОЙ ПРОЦЕСС:
            • Каждый ход вы можете сыграть одну карту
            • Карты бывают трёх типов: Атака, Защита, Лечение
            • После хода вы получаете новую карту
            """;

        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setTitle("Правила");
        a.setHeaderText("Dungeon Mayhem - Правила игры");
        TextArea ta = new TextArea(rules);
        ta.setEditable(false);
        ta.setWrapText(true);
        ta.setPrefSize(400, 300);
        a.getDialogPane().setContent(ta);
        a.showAndWait();
    }

    @FXML
    public void surrender() {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Сдаться");
        alert.setHeaderText("Вы уверены, что хотите сдаться?");
        alert.setContentText("Это приведёт к немедленному поражению.");

        if (alert.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
            if (client == null) {
                onGameOver(false, 0, 0);
            } else {
                if (networkController != null) {
                    networkController.sendChat("Игрок сдался!");
                    networkController.sendSurrender();
                }
                setPlayerCardsEnabled(false);
                stopTimer();
                showGameOverDialog("Вы сдались!", false);
            }
        }
    }

    @FXML
    public void returnToMenu() {
        cleanup();
        Platform.runLater(() -> {
            try {
                Stage st = (Stage) gameChatArea.getScene().getWindow();
                st.close();
                MainMenuController.showMainMenu();
            } catch (Exception ignored) {}
        });
    }

    private void updateTurnVisuals() {
        boolean enabled;
        if (client != null) {
            enabled = (serverTurnKnown == null) ? true : isMyTurn;
        } else {
            enabled = (engine != null && engine.isPlayerTurn());
        }

        for (var node : playerCardsContainer.getChildren()) {
            node.setDisable(!enabled);
            node.setOpacity(enabled ? 1.0 : 0.45);
        }

        turnIndicator.setText(enabled ? "Ваш ход" : "Ход противника");
    }

    private void updateTurnVisualsDisableAll() {
        for (var node : playerCardsContainer.getChildren()) {
            node.setDisable(true);
            node.setOpacity(0.45);
        }
        turnIndicator.setText("Игра окончена");
    }

    private void setPlayerCardsEnabled(boolean enabled) {
        this.isMyTurn = enabled;
        if (client != null) serverTurnKnown = enabled ? true : null;
        for (var node : playerCardsContainer.getChildren()) {
            node.setDisable(!enabled);
            node.setOpacity(enabled ? 1.0 : 0.45);
        }
        turnIndicator.setText(enabled ? "Ваш ход" : "Ход противника");
    }

    private void showGameOverDialog(String message, boolean isVictory) {
        Alert alert = new Alert(isVictory ? Alert.AlertType.INFORMATION : Alert.AlertType.WARNING);
        alert.setTitle(isVictory ? "🎉 ПОБЕДА!" : "💀 ПОРАЖЕНИЕ");
        alert.setHeaderText(isVictory ? "Вы победили!" : "Игра окончена");
        alert.setContentText(message);

        ButtonType newGameButton = new ButtonType("Новая игра");
        ButtonType menuButton = new ButtonType("В главное меню");
        alert.getButtonTypes().setAll(newGameButton, menuButton);

        alert.showAndWait().ifPresent(buttonType -> {
            if (buttonType == newGameButton) {
                cleanup();
                initialize();
            } else if (buttonType == menuButton) {
                returnToMenu();
            }
        });
    }

    public void cleanup() {
        stopTimer();
        indicatorAnimation.stop();
        victoryPulseAnimation.stop();

        if (networkController != null) {
            networkController.shutdown();
            networkController = null;
        }
    }
}
