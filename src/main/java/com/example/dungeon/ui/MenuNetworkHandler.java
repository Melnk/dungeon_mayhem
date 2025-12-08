package com.example.dungeon.ui;

import com.example.dungeon.game.Card;
import com.example.dungeon.game.GameState;
import com.example.dungeon.network.GameNetworkController;

/**
 * Обработчик сетевых событий для главного меню.
 * Делегирует события в MainMenuController корректно, включая разбор строкового чата.
 */
public class MenuNetworkHandler implements GameNetworkController.NetworkListener {

    private final MainMenuController menuController;

    public MenuNetworkHandler(MainMenuController menuController) {
        this.menuController = menuController;
    }

    @Override
    public void onChatMessage(String sender, String message) {
        if (message == null) message = "";

        String realSender = sender;
        String realMessage = message;

        // Если сервер/контроллер присылает единый стринг вида "Игрок 1: текст",
        // распарсим и передадим отдельно sender и message.
        if ((realSender == null || realSender.isEmpty() || "Игрок".equals(realSender))
            && message.contains(": ")) {
            int idx = message.indexOf(": ");
            String possibleSender = message.substring(0, idx).trim();
            String possibleMsg = message.substring(idx + 2);
            // Доп. проверка: если possibleSender короткое и выглядит как "Игрок" или "Игрок N" или имя пользователя
            if (!possibleSender.isEmpty() && possibleSender.length() <= 32) {
                realSender = possibleSender;
                realMessage = possibleMsg;
            }
        }

        // Если в итоге sender оказался пустым — выводим как системное сообщение
        if (realSender == null || realSender.trim().isEmpty()) {
            menuController.addChatMessage("", realMessage);
        } else {
            menuController.addChatMessage(realSender, realMessage);
        }
    }

    @Override
    public void onGameUpdate(GameState state) {
        menuController.handleGameUpdate(state);
    }

    @Override
    public void onCardPlayed(Card card) {
        // Не используется в меню
    }

    @Override
    public void onConnected(String info) {
        menuController.handleConnectionStatus(true, info);
    }

    @Override
    public void onDisconnected(String reason) {
        menuController.handleConnectionStatus(false, reason);
    }

    @Override
    public void onError(String error) {
        menuController.handleNetworkError(error);
    }

    @Override
    public void onYourTurn(boolean isYourTurn) {
        // Не используется в меню
    }

    @Override
    public void onGameOver(String result) {
        menuController.addChatMessage("Система", "🏆 Игра завершена: " + result);
    }

    @Override
    public void onPlayerInfo(String info) {
        menuController.addChatMessage("Система", "🎭 " + info);
    }
}
