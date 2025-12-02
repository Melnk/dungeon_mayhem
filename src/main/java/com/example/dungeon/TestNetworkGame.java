package com.example.dungeon;

public class TestNetworkGame {
    public static void main(String[] args) {
        System.out.println("=== ИНСТРУКЦИЯ ПО ТЕСТИРОВАНИЮ СЕТЕВОЙ ИГРЫ ===\n");

        System.out.println("1. ЗАПУСК СЕРВЕРА:");
        System.out.println("   - Откройте первое окно приложения");
        System.out.println("   - Нажмите 'Создать сервер'");
        System.out.println("   - Должно появиться: 'Сервер запущен на порту 12345'\n");

        System.out.println("2. ПОДКЛЮЧЕНИЕ КЛИЕНТА:");
        System.out.println("   - Откройте второе окно приложения");
        System.out.println("   - Введите 'localhost' в поле IP");
        System.out.println("   - Нажмите 'Подключиться'");
        System.out.println("   - Должно появиться: 'Успешное подключение'\n");

        System.out.println("3. ЗАПУСК ИГРЫ:");
        System.out.println("   - В ОБОИХ окнах нажмите 'Начать игру'");
        System.out.println("   - Должны открыться два игровых окна\n");

        System.out.println("4. ТЕСТИРОВАНИЕ ИГРЫ:");
        System.out.println("   - В первом окне (Игрок 1) кликните на карту");
        System.out.println("   - Во втором окне (Игрок 2) должна обновиться информация");
        System.out.println("   - Теперь ход перейдет ко второму игроку");
        System.out.println("   - Повторите для второго игрока\n");

        System.out.println("5. ЧТО ДОЛЖНО РАБОТАТЬ:");
        System.out.println("   - Синхронизация здоровья и щитов");
        System.out.println("   - Поочередные ходы");
        System.out.println("   - Чат между игроками");
        System.out.println("   - Определение победителя\n");

        System.out.println("6. ВОЗМОЖНЫЕ ПРОБЛЕМЫ И РЕШЕНИЯ:");
        System.out.println("   - Проблема: Белый экран при запуске игры");
        System.out.println("     Решение: Проверьте game.fxml и CSS файлы\n");

        System.out.println("   - Проблема: Карты не кликаются");
        System.out.println("     Решение: Проверьте isMyTurn в GameController\n");

        System.out.println("   - Проблема: Сообщения не приходят");
        System.out.println("     Решение: Проверьте порт 12345 и брандмауэр\n");

        System.out.println("=== УДАЧНОГО ТЕСТИРОВАНИЯ! ===");
    }
}
