package com.example.dungeon.network;

import java.io.*;
import java.net.*;
import java.util.concurrent.*;
import java.util.function.*;
import com.example.dungeon.game.Card;
import com.example.dungeon.ui.MainMenuController;

public class Client implements Runnable {
    private String host;
    private int port;
    private Consumer<Object> messageHandler;
    private boolean connected;
    private Socket socket;
    private ObjectOutputStream out;
    private ObjectInputStream in;
    private ExecutorService messageProcessor;

    public Client(String host, int port, Consumer<Object> messageHandler) throws IOException {
        this.host = host;
        this.port = port;
        this.messageHandler = messageHandler;
        this.connected = false;
        this.messageProcessor = Executors.newSingleThreadExecutor();
    }

    @Override
    public void run() {
        try {
            socket = new Socket(host, port);
            socket.setSoTimeout(5000); // Таймаут 5 секунд

            out = new ObjectOutputStream(socket.getOutputStream());
            in = new ObjectInputStream(socket.getInputStream());

            connected = true;
            System.out.println("Успешно подключено к " + host + ":" + port);

            // Уведомляем UI об успешном подключении
            messageHandler.accept("CONNECTED:Успешное подключение");

            // Основной цикл приема сообщений
            while (connected && !socket.isClosed()) {
                try {
                    NetworkMessage message = (NetworkMessage) in.readObject();
                    processMessage(message);
                } catch (SocketTimeoutException e) {
                    // Таймаут - продолжаем слушать
                    continue;
                } catch (EOFException | SocketException e) {
                    System.out.println("Соединение разорвано");
                    break;
                }
            }
        } catch (ConnectException e) {
            messageHandler.accept("ERROR:Не удалось подключиться к серверу");
            System.err.println("Ошибка подключения: " + e.getMessage());
        } catch (IOException | ClassNotFoundException e) {
            messageHandler.accept("ERROR:Ошибка соединения: " + e.getMessage());
            System.err.println("Ошибка клиента: " + e.getMessage());
        } finally {
            disconnect();
        }
    }

    private void processMessage(NetworkMessage message) {
        // Обрабатываем сообщение в отдельном потоке
        messageProcessor.submit(() -> {
            messageHandler.accept(message);
        });
    }

    public synchronized void sendMessage(NetworkMessage message) {
        if (!connected || out == null) {
            System.err.println("Нельзя отправить сообщение: нет подключения");
            return;
        }

        try {
            out.writeObject(message);
            out.flush();
            System.out.println("Сообщение отправлено: " + message.getType());
        } catch (IOException e) {
            System.err.println("Ошибка отправки сообщения: " + e.getMessage());
            disconnect();
        }
    }

    public void sendChatMessage(String message) {
        sendMessage(new NetworkMessage(MessageType.CHAT_MESSAGE, message));
    }

    public void playCard(Card card) {
        sendMessage(new NetworkMessage(MessageType.CARD_PLAYED, card));
    }

    private void disconnect() {
        connected = false;
        messageProcessor.shutdown();

        try {
            if (in != null) in.close();
            if (out != null) out.close();
            if (socket != null && !socket.isClosed()) socket.close();

            // Уведомляем UI об отключении
            messageHandler.accept("DISCONNECTED:Соединение разорвано");
        } catch (IOException e) {
            System.err.println("Ошибка при отключении: " + e.getMessage());
        }
    }

    public boolean isConnected() {
        return connected;
    }

    public void stop() {
        disconnect();
    }
}
