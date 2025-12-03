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
import java.util.List;

/**
 * GameController — исправленная версия:
 *  - убран вызов несуществующего CardMapper
 *  - клики по картам в сетевой игре разрешаются, если визуально карта доступна (исправлена логика)
 *  - корректная синхронизация serverTurnKnown при приходе статусов / обновлений
 */
public class GameController implements GameEventListener, GameNetworkController.NetworkListener {

    @FXML private Label playerHealthLabel;
    @FXML private Label playerShieldLabel;
    @FXML private Label opponentHealthLabel;
    @FXML private Label opponentShieldLabel;
    @FXML private Label gameStatusLabel;
    @FXML private Label turnIndicator;
    @FXML private Label lastActionLabel;
    @FXML private Label gameTimerLabel;

    @FXML private Canvas playerHealthCanvas;
    @FXML private Canvas opponentHealthCanvas;
    @FXML private Canvas battleAnimationCanvas;

    @FXML private HBox playerCardsContainer;
    @FXML private HBox opponentCardsContainer;

    @FXML private TextArea gameChatArea;
    @FXML private TextField gameMessageField;

    private GameEngine engine;
    private CardViewFactory cardFactory;
    private HealthBarRenderer hbRenderer;
    private AnimationManager animationManager;
    private ChatService chatService;
    private GameNetworkController networkController;
    private Client client;

    private boolean isMyTurn = true;
    /** Для сетевой игры: null = сервер ещё не сообщил чей ход; true/false = известно */
    private Boolean serverTurnKnown = null;
    private Timeline gameTimer;
    private Instant timerStart;

    @Setter
    private GameState initialGameState;

    public GameController() {}

    @FXML
    public void initialize() {
        this.engine = new GameEngine();
        this.engine.setListener(this);

        this.cardFactory = new CardViewFactory();
        this.hbRenderer = new HealthBarRenderer();
        this.animationManager = new AnimationManager(battleAnimationCanvas);
        this.chatService = new ChatService(gameChatArea);

        serverTurnKnown = null;

        if (initialGameState != null) {
            applyInitialGameState(initialGameState);
            initialGameState = null;
        }

        if (client == null) {
            engine.startSinglePlayer();
            isMyTurn = engine.isPlayerTurn();
            updateTurnVisuals();
            startTimer();
        } else {
            chatService.addChatMessage("Сеть", "Ожидание состояния от сервера...");
        }
    }

    public void setClient(Client client) {
        this.client = client;
        if (client != null) {
            this.networkController = new GameNetworkController(client, this);
            serverTurnKnown = null; // ждём GAME_UPDATE
        }
    }

    // timer
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
        String rules = "Правила...";
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setTitle("Правила");
        a.setHeaderText("Dungeon Mayhem - Правила");
        TextArea ta = new TextArea(rules);
        ta.setEditable(false);
        ta.setWrapText(true);
        a.getDialogPane().setContent(ta);
        a.showAndWait();
    }

    @FXML public void surrender() {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Сдаться");
        alert.setHeaderText("Вы уверены?");
        if (alert.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
            if (client == null) {
                onGameOver(false, 0, 0);
            } else {
                if (networkController != null) networkController.sendChat("PLAYER_SURRENDER");
                setPlayerCardsEnabled(false);
            }
        }
    }

    @FXML public void returnToMenu() {
        cleanup();
        Platform.runLater(() -> {
            try {
                Stage st = (Stage) gameChatArea.getScene().getWindow();
                st.close();
            } catch (Exception ignored) {}
        });
    }

    // GameEventListener
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

            // определяем визуальную доступность
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
                    // allow click if either:
                    // - offline and engine says it's player's turn
                    // - online and (server told whose turn OR we allow play when status unknown) AND isMyTurn
                    boolean allowLocalPlay = (client == null && engine != null && engine.isPlayerTurn());
                    boolean allowNetworkSend;
                    if (client != null) {
                        // если сервер ещё не сообщил чей ход — позволим отправить (чтобы не блокировать UX),
                        // после отправки мы сразу поставим serverTurnKnown=null и заблокируем интерфейс.
                        allowNetworkSend = isMyTurn;
                    } else allowNetworkSend = false;

                    if (!allowLocalPlay && !allowNetworkSend) {
                        chatService.addChatMessage("Система", "Сейчас не ваш ход!");
                        return;
                    }

                    if (client != null) {
                        if (networkController != null) {
                            networkController.playCard(card);
                            chatService.addChatMessage("Вы", card.getName());
                            // ожидаем ответ от сервера — пометим как неизвестный и заблокируем UI
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
        });
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
            if (status != null && status.toUpperCase().contains("ВАШ")) isMyTurn = true;
            else if (status != null && status.toUpperCase().contains("ХОД ПРОТИВНИКА")) isMyTurn = false;
            // если статус пришёл — считаем, что сервер сообщил чей ход
            serverTurnKnown = true;
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

    // NetworkListener
    @Override public void onChatMessage(String sender, String message) { chatService.addChatMessage(sender, message); }

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
    public void onCardPlayed(com.example.dungeon.game.Card card) {
        // получаем уже локальный тип Card из пакета com.example.dungeon.game — используем напрямую
        onCardPlayed(card, true);
    }

    @Override public void onConnected(String info) { chatService.addChatMessage("Сеть", info); }
    @Override public void onDisconnected(String reason) {
        chatService.addChatMessage("Сеть", "Отключено: " + reason);
        // безопасно блокируем карты, чтобы ничего не сломать
        setPlayerCardsEnabled(false);
    }
    @Override public void onError(String error) { chatService.addChatMessage("Сеть", "Ошибка: " + error); }

    // helpers
    private void applyInitialGameState(GameState state) {
        if (state == null) return;
        Player me = state.getCurrentPlayer();
        Player opp = state.getOpponentPlayer();
        if (me != null && opp != null) {
            onHealthUpdated(me.getHealth(), me.getShield(), opp.getHealth(), opp.getShield());
            onHandUpdated(me.getHand() == null ? List.of() : me.getHand());
            onOpponentHandCountUpdated(opp.getHand() == null ? 0 : opp.getHand().size());
            isMyTurn = state.isPlayerTurn();
            serverTurnKnown = true;
            if (isMyTurn) onGameStatusUpdated("🎯 ВАШ ХОД"); else onGameStatusUpdated("⏳ ХОД ПРОТИВНИКА");
            startTimer();
        } else {
            chatService.addChatMessage("Система", "Получено неполное состояние от сервера.");
        }
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

    public void cleanup() {
        stopTimer();
        if (networkController != null) {
            networkController.shutdown();
            networkController = null;
        }
    }
}
