package com.finquity.ui;

import com.finquity.ui.view.LoginScreen;
import com.finquity.ui.view.Screen;
import java.io.InputStream;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.scene.text.Font;
import javafx.stage.Stage;

/*
 * Point d'entrée de l'interface JavaFX.
 * Le serveur Spring Boot doit tourner à côté (mvn spring-boot:run dans un autre terminal).
 * Lancement : mvn javafx:run
 */
public class FinquityApp extends Application implements Navigator {

    private static final String SERVER = "http://localhost:8080";
    private static final String[] FONTS = {
        "CormorantGaramond-Light.ttf", "CormorantGaramond-LightItalic.ttf", "CormorantGaramond-Regular.ttf",
        "DMSans-Light.ttf", "DMSans-Regular.ttf", "DMSans-Medium.ttf"
    };

    private final ApiClient api = new ApiClient(SERVER);
    private Scene scene;

    @Override
    public void start(Stage stage) {
        loadFonts();
        scene = new Scene(new StackPane(), 1280, 800);
        scene.getStylesheets().add(getClass().getResource("/ui/finquity.css").toExternalForm());
        stage.setTitle("Finquity — Precision. Clarity. Finquity.");
        stage.setMinWidth(1100);
        stage.setMinHeight(700);
        stage.setScene(scene);
        show(new LoginScreen(this));
        stage.show();
    }

    // Charge les polices du site (Cormorant Garamond et DM Sans) rangées dans resources/ui/fonts.
    private void loadFonts() {
        for (String name : FONTS) {
            try (InputStream in = getClass().getResourceAsStream("/ui/fonts/" + name)) {
                if (in != null) {
                    Font.loadFont(in, 13);
                }
            } catch (Exception e) {
                System.err.println("Police non chargée : " + name);
            }
        }
    }

    @Override
    public void show(Screen screen) {
        scene.setRoot(screen.build());
    }

    @Override
    public ApiClient api() {
        return api;
    }

    public static void main(String[] args) {
        launch(args);
    }
}
