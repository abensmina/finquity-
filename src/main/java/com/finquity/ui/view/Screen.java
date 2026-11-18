package com.finquity.ui.view;

import com.finquity.ui.ApiClient;
import com.finquity.ui.ApiException;
import com.finquity.ui.Navigator;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Function;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;

/*
 * Classe mère de tous les écrans.
 * Elle construit ce qui est commun (menu de gauche, barre du haut, zone d'erreur)
 * et laisse chaque écran fournir son propre contenu : c'est le patron "méthode template".
 */
public abstract class Screen {

    protected static final NumberFormat EUROS = NumberFormat.getCurrencyInstance(Locale.FRANCE);

    // Les entrées du menu : le texte et comment créer l'écran correspondant.
    private record MenuEntry(String label, Class<? extends Screen> type, Function<Navigator, Screen> factory) {
    }

    private static final MenuEntry[] MENU = {
        new MenuEntry("Tableau de bord", DashboardScreen.class, DashboardScreen::new),
        new MenuEntry("Import des comptes", ImportScreen.class, ImportScreen::new),
        new MenuEntry("Analyse financière", AnalysisScreen.class, AnalysisScreen::new),
        new MenuEntry("Portefeuille", PortfolioScreen.class, PortfolioScreen::new),
        new MenuEntry("Simulation", SimulationScreen.class, SimulationScreen::new),
        new MenuEntry("Rapport PDF", ReportScreen.class, ReportScreen::new),
        new MenuEntry("Mon compte", ProfileScreen.class, ProfileScreen::new),
    };

    protected final Navigator nav;
    private final Label errorLabel = new Label();
    private final Label infoLabel = new Label();

    protected Screen(Navigator nav) {
        this.nav = nav;
        setupMessage(errorLabel, "message-error");
        setupMessage(infoLabel, "message-ok");
    }

    // ---------- Ce que chaque écran doit (ou peut) définir ----------

    // Titre de la barre du haut.
    protected abstract String title();

    // Sous-titre de la barre du haut.
    protected abstract String subtitle();

    // Le contenu propre à l'écran.
    protected abstract Node content();

    // Par défaut, un écran affiche le menu. L'écran de connexion le désactive.
    protected boolean hasMenu() {
        return true;
    }

    // ---------- Construction commune ----------

    public final Parent build() {
        if (!hasMenu()) {
            // Sans menu, l'écran place lui-même la zone de messages (voir messages()).
            BorderPane root = new BorderPane(content());
            root.getStyleClass().add("app");
            return root;
        }
        VBox page = new VBox(24, errorLabel, infoLabel, content());
        page.getStyleClass().add("page");
        ScrollPane scroll = new ScrollPane(page);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("page-scroll");

        BorderPane main = new BorderPane(scroll);
        main.setTop(topbar());
        BorderPane root = new BorderPane(main);
        root.setLeft(sidebar());
        root.getStyleClass().add("app");
        return root;
    }

    private HBox topbar() {
        Label t = new Label(title());
        t.getStyleClass().add("topbar-title");
        Label s = new Label(subtitle());
        s.getStyleClass().add("topbar-sub");
        VBox texts = new VBox(2, t, s);
        texts.setAlignment(Pos.CENTER_LEFT);
        HBox bar = new HBox(texts);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.getStyleClass().add("topbar");
        return bar;
    }

    private VBox sidebar() {
        Text a = new Text("Finqui");
        a.getStyleClass().add("logo-text");
        Text b = new Text("ty");
        b.getStyleClass().addAll("logo-text", "logo-gold");
        TextFlow logo = new TextFlow(a, b);
        logo.getStyleClass().add("sidebar-logo");

        Label section = new Label("NAVIGATION");
        section.getStyleClass().add("sidebar-section");

        VBox side = new VBox(logo, section);
        for (MenuEntry entry : MENU) {
            Button item = new Button(entry.label());
            item.getStyleClass().add("nav-item");
            if (entry.type() == getClass()) {
                item.getStyleClass().add("active");
            }
            item.setMaxWidth(Double.MAX_VALUE);
            item.setOnAction(e -> nav.show(entry.factory().apply(nav)));
            side.getChildren().add(item);
        }
        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);
        Button logout = new Button("Se déconnecter");
        logout.getStyleClass().add("nav-item");
        logout.setMaxWidth(Double.MAX_VALUE);
        logout.setOnAction(e -> call(() -> {
            api().logout();
            return null;
        }, ignored -> nav.show(new LoginScreen(nav))));
        side.getChildren().addAll(spacer, logout);
        side.getStyleClass().add("sidebar");
        side.setPrefWidth(220);
        side.setMinWidth(220);
        return side;
    }

    // ---------- Outils pour les écrans ----------

    protected ApiClient api() {
        return nav.api();
    }

    // La zone des messages (erreur / confirmation), pour un écran sans menu.
    protected VBox messages() {
        return new VBox(8, errorLabel, infoLabel);
    }

    protected void showError(String message) {
        infoLabel.setVisible(false);
        infoLabel.setManaged(false);
        errorLabel.setText(message);
        errorLabel.setVisible(true);
        errorLabel.setManaged(true);
    }

    protected void showInfo(String message) {
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);
        infoLabel.setText(message);
        infoLabel.setVisible(true);
        infoLabel.setManaged(true);
    }

    protected void clearMessages() {
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);
        infoLabel.setVisible(false);
        infoLabel.setManaged(false);
    }

    private static void setupMessage(Label label, String style) {
        label.getStyleClass().add(style);
        label.setWrapText(true);
        label.setMaxWidth(Double.MAX_VALUE);
        label.setVisible(false);
        label.setManaged(false);
    }

    // Grand titre façon site : une partie normale, une partie en italique dorée.
    protected static TextFlow headline(String normal, String italic) {
        Text a = new Text(normal);
        a.getStyleClass().add("headline");
        Text b = new Text(italic);
        b.getStyleClass().addAll("headline", "headline-em");
        return new TextFlow(a, b);
    }

    // Petit titre de section en majuscules espacées.
    protected static Label sectionTitle(String text) {
        Label l = new Label(text.toUpperCase());
        l.getStyleClass().add("section-title");
        return l;
    }

    // Carte d'indicateur : libellé, valeur, petite ligne d'explication.
    protected static VBox kpi(String label, String value, String sub, String valueStyle) {
        Label l = new Label(label.toUpperCase());
        l.getStyleClass().add("kpi-label");
        Label v = new Label(value);
        v.getStyleClass().add("kpi-val");
        if (valueStyle != null) {
            v.getStyleClass().add(valueStyle);
        }
        Label s = new Label(sub);
        s.getStyleClass().add("kpi-sub");
        s.setWrapText(true);
        VBox card = new VBox(8, l, v, s);
        card.getStyleClass().add("card");
        HBox.setHgrow(card, Priority.ALWAYS);
        card.setMaxWidth(Double.MAX_VALUE);
        return card;
    }

    // Champ de formulaire avec son libellé au-dessus.
    protected static VBox field(String label, TextField input) {
        Label l = new Label(label.toUpperCase());
        l.getStyleClass().add("field-label");
        return new VBox(6, l, input);
    }

    protected static Button primaryButton(String text) {
        Button b = new Button(text.toUpperCase());
        b.getStyleClass().add("btn-gold");
        return b;
    }

    protected static Button secondaryButton(String text) {
        Button b = new Button(text.toUpperCase());
        b.getStyleClass().add("btn-outline");
        return b;
    }

    // Lit un nombre tapé par l'utilisateur (espaces et virgule acceptés).
    protected static double parseNumber(TextField input, String name) {
        String raw = input.getText().replace(" ", "").replace(" ", "").replace(",", ".");
        if (raw.isEmpty()) {
            throw new IllegalArgumentException("Le champ « " + name + " » est vide.");
        }
        try {
            return Double.parseDouble(raw);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Le champ « " + name + " » doit être un nombre.");
        }
    }

    protected static String percent(double value) {
        return Double.isNaN(value) ? "—" : String.format(Locale.FRANCE, "%.1f %%", value * 100);
    }

    protected static String ratio(double value) {
        return Double.isNaN(value) || Double.isInfinite(value) ? "—" : String.format(Locale.FRANCE, "%.2f", value);
    }

    protected static String profileLabel(String code) {
        return switch (code) {
            case "PRUDENT" -> "Prudent";
            case "EQUILIBRE" -> "Équilibré";
            case "DYNAMIQUE" -> "Dynamique";
            default -> code;
        };
    }

    // Un appel au serveur qui peut échouer.
    @FunctionalInterface
    protected interface ApiCall<T> {
        T run() throws ApiException;
    }

    /*
     * Lance l'appel au serveur dans un autre thread (la fenêtre ne se fige pas),
     * puis revient dans le thread JavaFX pour mettre à jour l'écran.
     */
    protected <T> void call(ApiCall<T> request, Consumer<T> onSuccess) {
        Thread worker = new Thread(() -> {
            try {
                T result = request.run();
                Platform.runLater(() -> onSuccess.accept(result));
            } catch (ApiException e) {
                Platform.runLater(() -> showError(e.getMessage()));
            }
        });
        worker.setDaemon(true);
        worker.start();
    }
}
