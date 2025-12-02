package com.example.dungeon.ui;

import javafx.application.Platform;
import javafx.scene.control.TextArea;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public class ChatService {
    private final TextArea chatArea;
    private final DateTimeFormatter timeFmt = DateTimeFormatter.ofPattern("HH:mm");

    public ChatService(TextArea chatArea) {
        this.chatArea = chatArea;
    }

    public void addChatMessage(String sender, String message) {
        String time = LocalTime.now().format(timeFmt);
        String formatted = String.format("[%s] %s: %s\n", time, sender, message);
        Platform.runLater(() -> {
            chatArea.appendText(formatted);
            chatArea.setScrollTop(Double.MAX_VALUE);
        });
    }
}
