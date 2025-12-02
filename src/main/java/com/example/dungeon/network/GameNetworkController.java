package com.example.dungeon.network;

import com.example.dungeon.game.GameEngine;
import com.example.dungeon.game.Card;
import com.example.dungeon.game.GameState;
import javafx.application.Platform;

/**
 * Отвечает за связь Client <-> GameEngine/UI.
 * Подписывается на client.messageHandler и транслирует события в GameEngine/UI.
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
    }

    public GameNetworkController(Client client, NetworkListener listener) {
        this.client = client;
        this.listener = listener;

        if (client != null) {
            // сохраняем старый обработчик, если нужно
            client.messageHandler = this::handleNetworkMessage;
        }
    }

    private void handleNetworkMessage(Object msg) {
        Platform.runLater(() -> {
            if (msg instanceof String) {
                String s = (String) msg;
                if (s.startsWith("CONNECTED:")) listener.onConnected(s.substring(10));
                else if (s.startsWith("DISCONNECTED:")) listener.onDisconnected(s.substring(13));
                else if (s.startsWith("ERROR:")) listener.onError(s.substring(6));
                else listener.onChatMessage("Система", s);
            } else if (msg instanceof NetworkMessage) {
                NetworkMessage nm = (NetworkMessage) msg;
                switch (nm.getType()) {
                    case CHAT_MESSAGE -> listener.onChatMessage("Игрок", (String) nm.getData());
                    case CARD_PLAYED -> listener.onCardPlayed((Card) nm.getData());
                    case GAME_UPDATE -> listener.onGameUpdate((GameState) nm.getData());
                    default -> System.out.println("[NET] Unknown type: " + nm.getType());
                }
            }
        });
    }

    public void sendChat(String text) {
        if (client != null && client.isConnected()) client.sendChatMessage(text);
    }

    public void playCard(Card card) {
        if (client != null && client.isConnected()) client.playCard(card);
    }

    public void shutdown() {
        // восстановление старого обработчика и т.п. (если нужно)
    }

}
