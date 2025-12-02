package com.example.dungeon.ui;

import com.example.dungeon.game.*;
import com.example.dungeon.network.Client;
import com.example.dungeon.network.GameNetworkController;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;
import javafx.util.Duration;
import lombok.Setter;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class GameController implements GameEventListener, GameNetworkController.NetworkListener {

    // FXML
    @FXML private Label playerHealthLabel;
    @FXML private Label playerShieldLabel;
    @FXML private Label opponentHealthLabel;
    @FXML private Label opponentShieldLabel;
    @FXML private Label gameStatusLabel;
    @FXML private Label turnIndicator;
    @FXML private Label lastActionLabel;

    // таймер сверху — добавьте в FXML Label fx:id="gameTimerLabel"
    @FXML private Label gameTimerLabel;

    @FXML private Canvas playerHealthCanvas;
    @FXML private Canvas opponentHealthCanvas;
    @FXML private Canvas battleAnimationCanvas;

    @FXML private HBox playerCardsContainer;
    @FXML private HBox opponentCardsContainer;

    @FXML private TextArea gameChatArea;
    @FXML private TextField gameMessageField;

    // collaborators
    private GameEngine engine;
    private CardViewFactory cardFactory;
    private HealthBarRenderer hbRenderer;
    private AnimationManager animationManager;
    private ChatService chatService;
    private GameNetworkController networkController;
    private Client client;

    // state
    private boolean isMyTurn = true;
    private Timeline gameTimer;
    private Instant timerStart;

    // initial state from MainMenu (через setter)
    @Setter
    private GameState initialGameState;

    public GameController() {}

    @FXML
    public void initialize() {
        System.out.println("GameController initialize()");
        this.engine = new GameEngine();
        this.engine.setListener(this);

        this.cardFactory = new CardViewFactory();
        this.hbRenderer = new HealthBarRenderer();
        this.animationManager = new AnimationManager(battleAnimationCanvas);
        this.chatService = new ChatService(gameChatArea);

        // Если initialGameState задан до initialize, применим
        if (initialGameState != null) {
            applyInitialGameState(initialGameState);
            initialGameState = null;
        }

        // если нет сетевого клиента — старт одиночной игры
        if (client == null) {
            engine.startSinglePlayer();
            // После старта оффлайна
            if (client == null) {
                engine.startSinglePlayer();
                isMyTurn = engine.isPlayerTurn(); // явно синхронизируем флаг
                // Перерисовать руку (engine уведомит via listener.onHandUpdated); но вызовем updateTurnVisuals на всякий
                updateTurnVisuals();
                startTimer();
            }
            isMyTurn = engine.isPlayerTurn();
            startTimer();
            updateTurnVisuals();
        } else {
            chatService.addChatMessage("Сеть", "Ожидание состояния от сервера...");
        }
    }

    // сеттеры для MainMenu (через рефлексию)
    public void setClient(Client client) {
        this.client = client;
        if (client != null) {
            this.networkController = new GameNetworkController(client, this);
        }
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
        if (timerStart == null) { gameTimerLabel.setText("00:00"); return; }
        long elapsed = java.time.Duration.between(timerStart, Instant.now()).getSeconds();
        long mins = elapsed / 60;
        long secs = elapsed % 60;
        gameTimerLabel.setText(String.format("%02d:%02d", mins, secs));
    }

    // UI handlers
    @FXML public void sendGameChatMessage() {
        String msg = gameMessageField.getText().trim();
        if (msg.isEmpty()) return;
        if (networkController != null) networkController.sendChat(msg);
        chatService.addChatMessage("Вы", msg);
        gameMessageField.clear();
    }

    @FXML public void showRules() {
        // Используем ChatService/Alert аналогично ранее
        String rules = "Правила..."; // укорочено, можно разместить полный текст как раньше
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setTitle("Правила");
        a.setHeaderText("Dungeon Mayhem - Правила");
        TextArea ta = new TextArea(rules);
        ta.setEditable(false); ta.setWrapText(true);
        a.getDialogPane().setContent(ta);
        a.showAndWait();
    }

    @FXML public void surrender() {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Сдаться");
        alert.setHeaderText("Вы уверены?");
        if (alert.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
            engine.playCard(new Card(CardType.HEAL, "surrender-placeholder"), false); // просто чтобы перейти в окончание
            // Лучше: отправить событие на сервер; но для оффлайна покажем поражение:
            onGameOver(false, 0, 0);
        }
    }

    @FXML public void returnToMenu() {
        cleanup();
        // Закрываем окно
        Platform.runLater(() -> {
            try {
                Stage st = (Stage) gameChatArea.getScene().getWindow();
                st.close();
                // главное меню откроется из MainMenuController в старом коде
            } catch (Exception e) { /* ignore */ }
        });
    }

    // GameEventListener impl
    @Override
    public void onHealthUpdated(int playerHP, int playerShield, int opponentHP, int opponentShield) {
        Platform.runLater(() -> {
            playerHealthLabel.setText("❤ HP: " + playerHP);
            playerShieldLabel.setText("🛡 Щиты: " + playerShield);
            opponentHealthLabel.setText("❤ HP: " + opponentHP);
            opponentShieldLabel.setText("🛡 Щиты: " + opponentShield);
            hbRenderer.drawHealthBar(playerHealthCanvas.getGraphicsContext2D(), playerHP, playerShield, false);
            hbRenderer.drawHealthBar(opponentHealthCanvas.getGraphicsContext2D(), opponentHP, opponentShield, true);
        });
    }

    @Override
    public void onHandUpdated(List<Card> playerHand) {
        Platform.runLater(() -> {
            playerCardsContainer.getChildren().clear();
            // Синхронизируем флаг хода с движком (если оффлайн)
            if (engine != null) {
                isMyTurn = engine.isPlayerTurn();
            }

            for (int i = 0; i < playerHand.size(); i++) {
                Card c = playerHand.get(i);

                boolean enabledVisual = isMyTurn; // показываем как доступные/тусклые
                // при сетевой игре клики отправляем на сервер и НЕ выполняем локально engine.playCard
                var pane = cardFactory.createCardPane(c, i, enabledVisual, card -> {
                    // Защита: не даём нажимать вне хода
                    if (!isMyTurn) {
                        chatService.addChatMessage("Система", "Сейчас не ваш ход!");
                        return;
                    }

                    if (networkController != null) {
                        // Сетевая игра: отправляем ход на сервер и сразу блокируем карты до обновления от сервера
                        networkController.playCard(card);
                        chatService.addChatMessage("Вы", card.getName());
                        // Визуально блокируем карты сразу
                        setPlayerCardsEnabled(false);
                    } else {
                        // Оффлайн: применяем ход локально через engine
                        engine.playCard(card, false);
                        chatService.addChatMessage("Вы", card.getName());
                    }
                });

                playerCardsContainer.getChildren().add(pane);
            }

            // Применяем визуальный эффект (тусклость/доступность)
            updateTurnVisuals();
        });
    }

    private void setPlayerCardsEnabled(boolean enabled) {
        this.isMyTurn = enabled;
        for (var node : playerCardsContainer.getChildren()) {
            node.setDisable(!enabled);
            node.setOpacity(enabled ? 1.0 : 0.45);
        }
        turnIndicator.setText(enabled ? "Ваш ход" : "Ход противника");
    }

    @Override
    public void onOpponentHandCountUpdated(int count) {
        Platform.runLater(() -> {
            opponentCardsContainer.getChildren().clear();
            for (int i = 0; i < count; i++) {
                opponentCardsContainer.getChildren().add(cardFactory.createHiddenCard(i));
            }
        });
    }

    @Override
    public void onGameStatusUpdated(String status) {
        Platform.runLater(() -> {
            gameStatusLabel.setText(status);
            // синхронизируем локально isMyTurn флаг (для оффлайн engine уже делает это)
            if (status != null && status.contains("ВАШ")) isMyTurn = true;
            else if (status != null && status.contains("ХОД ПРОТИВНИКА")) isMyTurn = false;
            updateTurnVisuals();
        });
    }

    @Override
    public void onActionOccurred(String description) {
        Platform.runLater(() -> lastActionLabel.setText(description));
    }

    @Override
    public void onGameOver(boolean playerWon, int playerHP, int opponentHP) {
        Platform.runLater(() -> {
            stopTimer();
            updateTurnVisualsDisableAll();
            String title = playerWon ? "ПОБЕДА!" : "ПОРАЖЕНИЕ";
            Alert alert = new Alert(playerWon ? Alert.AlertType.INFORMATION : Alert.AlertType.WARNING);
            alert.setTitle(title);
            alert.setHeaderText(playerWon ? "🎉 ВЫ ПОБЕДИЛИ!" : "💀 ВЫ ПРОИГРАЛИ");
            alert.setContentText("Игра завершена.");
            alert.showAndWait();
            chatService.addChatMessage("Система", playerWon ? "ВЫ ПОБЕДИЛИ!" : "ВЫ ПРОИГРАЛИ.");
        });
    }

    @Override
    public void onCardPlayed(Card card, boolean byOpponent) {
        Platform.runLater(() -> {
            animationManager.showCardAnimation(card);
            chatService.addChatMessage(byOpponent ? "Противник" : "Вы", "сыграл: " + card.getName());
        });
    }

    // NetworkListener impl (коротко)
    @Override public void onChatMessage(String sender, String message) { chatService.addChatMessage(sender, message); }
    @Override
    public void onGameUpdate(GameState state) {
        Platform.runLater(() -> {
            if (state == null) return;
            this.isMyTurn = state.isPlayerTurn();
            applyInitialGameState(state); // или адаптер который у тебя есть
            updateTurnVisuals();
        });
    }
    @Override public void onCardPlayed(com.example.dungeon.game.Card card) { onCardPlayed(card, true); }
    @Override public void onConnected(String info) { chatService.addChatMessage("Сеть", info); }
    @Override public void onDisconnected(String reason) { chatService.addChatMessage("Сеть","Отключено: "+reason); }
    @Override public void onError(String error) { chatService.addChatMessage("Сеть","Ошибка: "+error); }

    // Вспомогательные методы
    private void applyInitialGameState(GameState state) {
        if (state == null) return;
        Player me = state.getCurrentPlayer();
        Player opp = state.getOpponentPlayer();
        if (me != null && opp != null) {
            onHealthUpdated(me.getHealth(), me.getShield(), opp.getHealth(), opp.getShield());
            onHandUpdated(me.getHand() == null ? List.of() : me.getHand());
            onOpponentHandCountUpdated(opp.getHand() == null ? 0 : opp.getHand().size());
            isMyTurn = state.isPlayerTurn();
            if (isMyTurn) onGameStatusUpdated("🎯 ВАШ ХОД"); else onGameStatusUpdated("⏳ ХОД ПРОТИВНИКА");
            startTimer();
        }
    }

    private void updateTurnVisuals() {
        boolean enabled = this.isMyTurn;
        for (var node : playerCardsContainer.getChildren()) {
            node.setDisable(!enabled);
            node.setOpacity(enabled ? 1.0 : 0.45);
        }
        // если нужно — обновляем индикатор и gameStatusLabel
        turnIndicator.setText(enabled ? "Ваш ход" : "Ход противника");
    }

    private void updateTurnVisualsDisableAll() {
        for (var node : playerCardsContainer.getChildren()) {
            node.setDisable(true);
            node.setOpacity(0.45);
        }
    }

    // cleanup
    public void cleanup() {
        stopTimer();
        if (networkController != null) {
            networkController.shutdown();
            networkController = null;
        }
    }
}
