package com.example.dungeon.network;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;

public class Server implements Runnable {
    private int port;
    private boolean running;

    public Server(int port) throws IOException {
        this.port = port;
        this.running = true;
    }

    @Override
    public void run() {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Сервер запущен на порту " + port);

            while (running) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("Клиент подключен: " + clientSocket.getInetAddress());

                // Здесь будет обработка клиента
                handleClient(clientSocket);
            }
        } catch (IOException e) {
            System.err.println("Ошибка сервера: " + e.getMessage());
        }
    }

    private void handleClient(Socket clientSocket) {
        // TODO: Реализовать обработку клиента
    }

    public void stop() {
        running = false;
    }
}
