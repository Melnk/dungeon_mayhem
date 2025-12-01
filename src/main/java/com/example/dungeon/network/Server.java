package com.example.dungeon.network;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import com.example.dungeon.game.*;

public class Server implements Runnable {
    private int port;
    private boolean running;
    private ServerSocket serverSocket;
    private List<ClientHandler> clients;
    private GameState gameState;
    private ExecutorService pool;

    public Server(int port) throws IOException {
        this.port = port;
        this.running = true;
        this.serverSocket = new ServerSocket(port);
        this.clients = new ArrayList<>();
        this.pool = Executors.newFixedThreadPool(2);

        // Инициализация игрового состояния
        Player player1 = new Player("Игрок 1");
        Player player2 = new Player("Игрок 2");
        this.gameState = new GameState(player1, player2, true, "Ожидание игроков");
    }

    @Override
    public void run() {
        System.out.println("Сервер запущен на порту " + port);

        try {
            while (running) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("Новое подключение: " + clientSocket.getInetAddress());

                if (clients.size() < 2) {
                    ClientHandler clientHandler = new ClientHandler(clientSocket, this);
                    clients.add(clientHandler);
                    pool.execute(clientHandler);

                    // Уведомляем всех о подключении
                    broadcast(new NetworkMessage(MessageType.PLAYER_JOIN,
                        "Игрок " + clients.size() + " подключился"));

                    if (clients.size() == 2) {
                        startGame();
                    }
                } else {
                    System.out.println("Игра уже заполнена");
                    clientSocket.close();
                }
            }
        } catch (IOException e) {
            System.err.println("Ошибка сервера: " + e.getMessage());
        } finally {
            shutdown();
        }
    }

    private void startGame() {
        System.out.println("Начало игры!");

        // Раздаем начальные карты
        dealInitialCards();

        // Отправляем начальное состояние всем игрокам
        broadcastGameState();
    }

    private void dealInitialCards() {
        // Каждому игроку по 3 начальные карты
        for (int i = 0; i < 3; i++) {
            gameState.getCurrentPlayer().getHand().add(new Card(CardType.ATTACK, "Атака"));
            gameState.getOpponentPlayer().getHand().add(new Card(CardType.DEFENSE, "Защита"));
        }
    }

    public synchronized void broadcast(NetworkMessage message) {
        for (ClientHandler client : clients) {
            client.sendMessage(message);
        }
    }

    public synchronized void broadcastGameState() {
        NetworkMessage message = new NetworkMessage(MessageType.GAME_UPDATE, gameState);
        broadcast(message);
    }

    public synchronized void handleCardPlayed(Card card, ClientHandler sender) {
        System.out.println("Карта сыграна: " + card.getName());

        // Применяем эффект карты
        applyCardEffect(card);

        // Меняем ход
        gameState = new GameState(
            gameState.getOpponentPlayer(),
            gameState.getCurrentPlayer(),
            !gameState.isPlayerTurn(),
            gameState.isPlayerTurn() ? "Ход противника" : "Ваш ход"
        );

        // Добавляем новую карту игроку
        gameState.getCurrentPlayer().getHand().add(drawRandomCard());

        // Отправляем обновленное состояние
        broadcastGameState();

        // Проверяем победу
        checkWinCondition();
    }

    private void applyCardEffect(Card card) {
        Player current = gameState.getCurrentPlayer();
        Player opponent = gameState.getOpponentPlayer();

        switch (card.getType()) {
            case ATTACK:
                opponent.takeDamage(2);
                System.out.println("Нанесено 2 урона");
                break;
            case DEFENSE:
                current.setShield(current.getShield() + 1);
                System.out.println("Добавлен 1 щит");
                break;
            case HEAL:
                current.setHealth(Math.min(10, current.getHealth() + 1));
                System.out.println("Восстановлено 1 HP");
                break;
        }
    }

    private Card drawRandomCard() {
        CardType[] types = CardType.values();
        Random random = new Random();
        CardType randomType = types[random.nextInt(types.length)];

        String[] names = {"Меч", "Щит", "Зелье", "Лук", "Посох", "Кинжал"};
        String randomName = names[random.nextInt(names.length)];

        return new Card(randomType, randomName);
    }

    private void checkWinCondition() {
        if (!gameState.getCurrentPlayer().isAlive()) {
            broadcast(new NetworkMessage(MessageType.CHAT_MESSAGE,
                "Игрок " + gameState.getOpponentPlayer().getName() + " победил!"));
        } else if (!gameState.getOpponentPlayer().isAlive()) {
            broadcast(new NetworkMessage(MessageType.CHAT_MESSAGE,
                "Игрок " + gameState.getCurrentPlayer().getName() + " победил!"));
        }
    }

    public void removeClient(ClientHandler client) {
        clients.remove(client);
        System.out.println("Клиент отключен. Осталось: " + clients.size());
    }

    public void shutdown() {
        running = false;
        pool.shutdown();
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            System.err.println("Ошибка при закрытии сервера: " + e.getMessage());
        }
    }

    // Внутренний класс для обработки клиентов
    private class ClientHandler implements Runnable {
        private Socket socket;
        private Server server;
        private ObjectOutputStream out;
        private ObjectInputStream in;

        public ClientHandler(Socket socket, Server server) {
            this.socket = socket;
            this.server = server;
        }

        @Override
        public void run() {
            try {
                out = new ObjectOutputStream(socket.getOutputStream());
                in = new ObjectInputStream(socket.getInputStream());

                // Отправляем приветственное сообщение
                sendMessage(new NetworkMessage(MessageType.CHAT_MESSAGE,
                    "Добро пожаловать в Dungeon Mayhem!"));

                // Основной цикл обработки сообщений
                while (running && !socket.isClosed()) {
                    try {
                        NetworkMessage message = (NetworkMessage) in.readObject();
                        handleMessage(message);
                    } catch (EOFException | SocketException e) {
                        break; // Клиент отключился
                    }
                }
            } catch (IOException | ClassNotFoundException e) {
                System.err.println("Ошибка обработки клиента: " + e.getMessage());
            } finally {
                disconnect();
            }
        }

        private void handleMessage(NetworkMessage message) {
            switch (message.getType()) {
                case CARD_PLAYED:
                    Card card = (Card) message.getData();
                    server.handleCardPlayed(card, this);
                    break;

                case CHAT_MESSAGE:
                    String chatMessage = (String) message.getData();
                    server.broadcast(new NetworkMessage(MessageType.CHAT_MESSAGE, chatMessage));
                    break;

                default:
                    System.out.println("Неизвестный тип сообщения: " + message.getType());
            }
        }

        public synchronized void sendMessage(NetworkMessage message) {
            try {
                out.writeObject(message);
                out.flush();
            } catch (IOException e) {
                System.err.println("Ошибка отправки сообщения: " + e.getMessage());
            }
        }

        private void disconnect() {
            try {
                if (in != null) in.close();
                if (out != null) out.close();
                if (socket != null && !socket.isClosed()) socket.close();
                server.removeClient(this);
            } catch (IOException e) {
                System.err.println("Ошибка при отключении: " + e.getMessage());
            }
        }
    }
}
