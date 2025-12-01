package com.example.dungeon.network;

import java.io.*;
import java.net.Socket;
import java.util.function.Consumer;

public class Client implements Runnable {
    private String host;
    private int port;
    private Consumer<Object> messageHandler;
    private boolean connected;

    public Client(String host, int port, Consumer<Object> messageHandler) throws IOException {
        this.host = host;
        this.port = port;
        this.messageHandler = messageHandler;
        this.connected = false;
    }

    @Override
    public void run() {
        try (Socket socket = new Socket(host, port)) {
            connected = true;
            System.out.println("Подключено к серверу " + host + ":" + port);

            // Здесь будет обработка входящих сообщений
            handleConnection(socket);

        } catch (IOException e) {
            System.err.println("Ошибка клиента: " + e.getMessage());
            connected = false;
        }
    }

    private void handleConnection(Socket socket) {
        // TODO: Реализовать обработку соединения
    }

    public boolean isConnected() {
        return connected;
    }

    public void sendMessage(Object message) {
        // TODO: Реализовать отправку сообщений
    }
}
