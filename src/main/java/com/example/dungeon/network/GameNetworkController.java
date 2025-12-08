package com.example.dungeon.network;

import com.example.dungeon.game.Card;
import com.example.dungeon.game.GameState;
import javafx.application.Platform;

/**
 * Адаптер между Client и UI. Делегирует входящие сообщения в NetworkListener.
 */
public class GameNetworkController {

    private final Client client;
    private final NetworkListener listener;

    public interface NetworkListener {
        void onChatMessage(String sender, String message);
        void onGameUpdate(GameState state);
        void onCardPlayed(Card card);
        void onConnected(String info);
        void onDisconnected(String reason);
        void onError(String error);
        void onYourTurn(boolean isYourTurn);
        void onGameOver(String result);
        void onPlayerInfo(String info);
    }

    public GameNetworkController(Client client, NetworkListener listener) {
        this.client = client;
        this.listener = listener;

        if (this.client != null) {
            // Прикрепляем обработчик сообщений клиента — клиент должен вызывать этот callback
            // при получении строки или NetworkMessage
            this.client.messageHandler = this::handleIncoming;
        }
    }

    /**
     * Приходящее сообщение от Client может быть String или NetworkMessage.
     */
    private void handleIncoming(Object msg) {
        // UI-вызовы должны выполняться в JavaFX-потоке
        Platform.runLater(() -> {
            if (msg instanceof String s) {
                handleStringMessage(s);
            } else if (msg instanceof NetworkMessage nm) {
                handleNetworkMessage(nm);
            } else {
                System.out.println("[NET] Unknown incoming message class: " + (msg == null ? "null" : msg.getClass()));
            }
        });
    }

    private void handleStringMessage(String s) {
        if (s.startsWith("CONNECTED:")) {
            listener.onConnected(s.substring("CONNECTED:".length()));
        } else if (s.startsWith("DISCONNECTED:")) {
            listener.onDisconnected(s.substring("DISCONNECTED:".length()));
        } else if (s.startsWith("ERROR:")) {
            listener.onError(s.substring("ERROR:".length()));
        } else {
            // Обычная строка — считаем системным чатом
            listener.onChatMessage("Система", s);
        }
    }

    private void handleNetworkMessage(NetworkMessage nm) {
        if (nm == null || nm.getType() == null) {
            System.out.println("[NET] Received null NetworkMessage or null type");
            return;
        }

        switch (nm.getType()) {
            case PLAYER_JOIN -> {
                // Сообщение о подключении нового игрока
                listener.onPlayerInfo(String.valueOf(nm.getData()));
            }
            case CHAT_MESSAGE -> {
                Object data = nm.getData();
                if (data instanceof String text) {
                    listener.onChatMessage("Игрок", text);
                } else {
                    listener.onChatMessage("Игрок", String.valueOf(data));
                }
            }
            case CARD_PLAYED -> listener.onCardPlayed((Card) nm.getData());
            case GAME_UPDATE -> listener.onGameUpdate((GameState) nm.getData());
            case YOUR_TURN -> {
                Object d = nm.getData();
                if (d instanceof Boolean b) listener.onYourTurn(b);
                else listener.onYourTurn(Boolean.parseBoolean(String.valueOf(d)));
            }
            case GAME_OVER -> listener.onGameOver(String.valueOf(nm.getData()));
            case PLAYER_INFO -> listener.onPlayerInfo(String.valueOf(nm.getData()));
            default -> System.out.println("[NET] Unknown type: " + nm.getType());
        }
    }

    // Отправка чата через клиент
    public void sendChat(String text) {
        if (client != null && client.isConnected()) {
            client.sendChatMessage(text);
        }
    }

    public void playCard(Card card) {
        if (client != null && client.isConnected()) {
            client.playCard(card);
        }
    }

    public void sendSurrender() {
        if (client != null && client.isConnected()) {
            client.sendChatMessage("PLAYER_SURRENDER");
        }
    }

    public void shutdown() {
        if (client != null) {
            client.stop();
        }
    }

    public boolean isConnected() {
        return client != null && client.isConnected();
    }
}
