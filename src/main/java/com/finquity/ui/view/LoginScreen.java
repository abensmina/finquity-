package com.finquity.ui.view;

import com.finquity.api.Dto.RegisterRequest;
import com.finquity.ui.Navigator;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;

// Connexion et création de compte (inscription de la PME et de son premier utilisateur).
public class LoginScreen extends Screen {

    private final TextField email = new TextField();
    private final PasswordField password = new PasswordField();
    private final PasswordField confirm = new PasswordField();
    private final TextField name = new TextField();
    private final TextField company = new TextField();
    private final TextField siren = new TextField();
    private final TextField sector = new TextField();
    private final VBox signupFields = new VBox(16);
    private final TextFlow formTitle = new TextFlow();
    private final Label formSub = new Label();
    private final Button signinTab = new Button("SE CONNECTER");
    private final Button signupTab = new Button("CRÉER UN COMPTE");
    private final Button submit = primaryButton("Se connecter");
    private boolean signupMode = false;

    public LoginScreen(Navigator nav) {
        super(nav);
    }

    @Override
    protected String title() {
        return "Connexion";
    }

    @Override
    protected String subtitle() {
        return "";
    }

    @Override
    protected boolean hasMenu() {
        return false;
    }

    @Override
    protected Node content() {
        HBox page = new HBox(leftPanel(), rightPanel());
        VBox.setVgrow(page, Priority.ALWAYS);
        return page;
    }

    // Partie gauche : la présentation, comme sur le site.
    private VBox leftPanel() {
        Text a = new Text("Finqui");
        a.getStyleClass().add("brand-text");
        Text b = new Text("ty");
        b.getStyleClass().addAll("brand-text", "logo-gold");
        TextFlow brand = new TextFlow(a, b);

        Label label = new Label("—  PLATEFORME FINANCIÈRE");
        label.getStyleClass().add("left-label");
        Text h1 = new Text("Analysez.\n");
        Text h2 = new Text("Placez.\n");
        Text h3 = new Text("Décidez.");
        h1.getStyleClass().add("left-headline");
        h2.getStyleClass().addAll("left-headline", "headline-em");
        h3.getStyleClass().add("left-headline");
        TextFlow headline = new TextFlow(h1, h2, h3);
        Label sub = new Label("Importez vos comptes annuels et obtenez un diagnostic, un profil de risque "
                + "et le montant que votre PME peut placer.");
        sub.getStyleClass().add("left-sub");
        sub.setWrapText(true);
        sub.setMaxWidth(360);

        VBox features = new VBox(12,
                feature("Import Excel des comptes annuels"),
                feature("Note de santé sur 100 et profil de risque"),
                feature("Portefeuille et simulation Monte-Carlo"),
                feature("Rapport PDF exportable"));

        VBox middle = new VBox(24, label, headline, sub, features);
        Region top = new Region();
        Region bottom = new Region();
        VBox.setVgrow(top, Priority.ALWAYS);
        VBox.setVgrow(bottom, Priority.ALWAYS);
        Label footer = new Label("Precision. Clarity. Finquity.");
        footer.getStyleClass().add("left-footer");

        VBox left = new VBox(brand, top, middle, bottom, footer);
        left.getStyleClass().add("login-left");
        HBox.setHgrow(left, Priority.ALWAYS);
        left.setMaxWidth(Double.MAX_VALUE);
        return left;
    }

    private HBox feature(String text) {
        Region dot = new Region();
        dot.getStyleClass().add("feat-dot");
        Label l = new Label(text);
        l.getStyleClass().add("feat");
        HBox row = new HBox(12, dot, l);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    // Partie droite : le formulaire.
    private VBox rightPanel() {
        signinTab.getStyleClass().add("mode-btn");
        signupTab.getStyleClass().add("mode-btn");
        signinTab.setMaxWidth(Double.MAX_VALUE);
        signupTab.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(signinTab, Priority.ALWAYS);
        HBox.setHgrow(signupTab, Priority.ALWAYS);
        signinTab.setOnAction(e -> setMode(false));
        signupTab.setOnAction(e -> setMode(true));
        HBox toggle = new HBox(signinTab, signupTab);
        toggle.getStyleClass().add("mode-toggle");

        email.setPromptText("vous@entreprise.fr");
        password.setPromptText("••••••••");
        confirm.setPromptText("••••••••");
        name.setPromptText("Jean Dupont");
        company.setPromptText("Acme SAS");
        siren.setPromptText("123456789");
        sector.setPromptText("Commerce, Industrie, Services...");
        formSub.getStyleClass().add("form-sub");
        formSub.setWrapText(true);

        signupFields.getChildren().addAll(field("Entreprise", company), field("Numéro SIREN", siren),
                field("Secteur d'activité", sector), field("Confirmer le mot de passe", confirm));

        submit.setDefaultButton(true); // la touche Entrée valide le formulaire
        submit.setMaxWidth(Double.MAX_VALUE);
        submit.setOnAction(e -> onSubmit());

        VBox nameBox = field("Nom complet", name);
        nameBox.visibleProperty().bind(signupFields.visibleProperty());
        nameBox.managedProperty().bind(signupFields.managedProperty());

        VBox form = new VBox(16, toggle, formTitle, formSub, messages(), nameBox, field("Adresse email", email),
                field("Mot de passe", password), signupFields, submit);
        form.setMaxWidth(400);
        form.setPrefWidth(400);
        setMode(false);

        VBox inner = new VBox(form);
        inner.setAlignment(Pos.CENTER);
        inner.getStyleClass().add("login-right");
        // Le formulaire d'inscription est long : on peut le faire défiler si la fenêtre est petite.
        ScrollPane scroll = new ScrollPane(inner);
        scroll.setFitToWidth(true);
        scroll.setFitToHeight(true);
        scroll.getStyleClass().add("page-scroll");
        VBox right = new VBox(scroll);
        VBox.setVgrow(scroll, Priority.ALWAYS);
        HBox.setHgrow(right, Priority.ALWAYS);
        right.setMaxWidth(Double.MAX_VALUE);
        return right;
    }

    private void setMode(boolean signup) {
        signupMode = signup;
        signupFields.setVisible(signup);
        signupFields.setManaged(signup);
        signinTab.getStyleClass().remove("active");
        signupTab.getStyleClass().remove("active");
        (signup ? signupTab : signinTab).getStyleClass().add("active");

        Text a = new Text(signup ? "Créer un " : "Bon ");
        a.getStyleClass().add("form-title");
        Text b = new Text(signup ? "compte" : "retour");
        b.getStyleClass().addAll("form-title", "headline-em");
        formTitle.getChildren().setAll(a, b);
        formSub.setText(signup ? "Inscrivez votre entreprise et lancez votre première analyse."
                : "Connectez-vous pour accéder à vos analyses.");
        submit.setText(signup ? "CRÉER MON COMPTE" : "SE CONNECTER");
        clearMessages();
    }

    private void onSubmit() {
        clearMessages();
        String mail = email.getText().trim();
        String pass = password.getText();
        if (mail.isEmpty() || pass.isEmpty()) {
            showError("Remplissez l'email et le mot de passe.");
            return;
        }
        if (!signupMode) {
            call(() -> api().login(mail, pass), user -> nav.show(new DashboardScreen(nav)));
            return;
        }
        if (!pass.equals(confirm.getText())) {
            showError("Les mots de passe ne correspondent pas.");
            return;
        }
        // Le serveur vérifie le reste (SIREN, force du mot de passe...) et renvoie un message clair.
        RegisterRequest request = new RegisterRequest(company.getText().trim(),
                siren.getText().replace(" ", ""), sector.getText().trim(), name.getText().trim(), mail, pass);
        call(() -> api().register(request), user -> nav.show(new ImportScreen(nav)));
    }
}
