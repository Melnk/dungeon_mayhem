package com.example.dungeon.ui;

import javafx.application.Platform;
import javafx.scene.control.TextArea;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public class ChatService {
    private final TextArea chatArea;
    private final DateTimeFormatter timeFormatter;

    public ChatService(TextArea chatArea) {
        this.chatArea = chatArea;
        this.timeFormatter = DateTimeFormatter.ofPattern("HH:mm");
    }

    /**
     * Добавляет сообщение в chatArea.
     * Если передан пустой или null sender — выводим только текст (например: системные сообщения).
     * Если передан sender — выводим "[HH:mm] Sender: message".
     */
    public void addChatMessage(String sender, String message) {
        Platform.runLater(() -> {
            String time = LocalTime.now().format(timeFormatter);
            String formattedMessage;

            if (sender == null || sender.trim().isEmpty()) {
                formattedMessage = String.format("[%s] %s", time, message);
            } else {
                formattedMessage = String.format("[%s] %s: %s", time, sender, message);
            }

            chatArea.appendText(formattedMessage + "\n");
            // Автопрокрутка вниз
            chatArea.setScrollTop(Double.MAX_VALUE);
        });
    }

    public void addSystemMessage(String message) {
        addChatMessage("Система", message);
    }

    public void clear() {
        Platform.runLater(chatArea::clear);
    }
}
