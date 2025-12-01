module com.example.dungeon_mayhem {
    requires javafx.controls;
    requires javafx.fxml;
    requires static lombok;


    opens com.example.dungeon to javafx.fxml;
    exports com.example.dungeon;
}
