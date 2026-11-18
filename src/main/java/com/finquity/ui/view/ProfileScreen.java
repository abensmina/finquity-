package com.finquity.ui.view;

import com.fasterxml.jackson.databind.JsonNode;
import com.finquity.api.Dto.AddUserRequest;
import com.finquity.ui.Navigator;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

// Mon compte : changer son mot de passe et ajouter un collègue sur le compte de l'entreprise.
public class ProfileScreen extends Screen {

    private final PasswordField oldPass = new PasswordField();
    private final PasswordField newPass = new PasswordField();
    private final TextField colName = new TextField();
    private final TextField colEmail = new TextField();
    private final PasswordField colPass = new PasswordField();
    private final VBox users = new VBox(8);
    private final Label me = new Label();

    public ProfileScreen(Navigator nav) {
        super(nav);
    }

    @Override
    protected String title() {
        return "Mon compte";
    }

    @Override
    protected String subtitle() {
        return "Mot de passe et utilisateurs de l'entreprise";
    }

    @Override
    protected Node content() {
        me.getStyleClass().add("company-meta");
        call(() -> api().me(), u -> me.setText("Connecté en tant que " + u.username() + " (" + u.email() + ")"));
        loadUsers();
        return new VBox(32, new VBox(6, headline("Mon ", "compte"), me), passwordCard(), colleagueCard(),
                sectionTitle("Utilisateurs de l'entreprise"), users);
    }

    private VBox passwordCard() {
        Label t = new Label("Changer mon mot de passe");
        t.getStyleClass().add("card-title");
        Button save = primaryButton("Enregistrer");
        save.setOnAction(e -> {
            clearMessages();
            call(() -> {
                api().changePassword(oldPass.getText(), newPass.getText());
                return null;
            }, ok -> {
                oldPass.clear();
                newPass.clear();
                showInfo("Mot de passe modifié.");
            });
        });
        HBox row = new HBox(16, field("Mot de passe actuel", oldPass), field("Nouveau mot de passe", newPass), save);
        row.setAlignment(Pos.BOTTOM_LEFT);
        VBox card = new VBox(16, t, row);
        card.getStyleClass().add("card");
        return card;
    }

    private VBox colleagueCard() {
        Label t = new Label("Ajouter un collègue");
        t.getStyleClass().add("card-title");
        Button add = secondaryButton("Ajouter");
        add.setOnAction(e -> {
            clearMessages();
            AddUserRequest req = new AddUserRequest(colName.getText().trim(), colEmail.getText().trim(),
                    colPass.getText());
            call(() -> api().addColleague(req), u -> {
                colName.clear();
                colEmail.clear();
                colPass.clear();
                showInfo(u.username() + " peut maintenant se connecter.");
                loadUsers();
            });
        });
        HBox row = new HBox(16, field("Nom", colName), field("Email", colEmail), field("Mot de passe", colPass), add);
        row.setAlignment(Pos.BOTTOM_LEFT);
        VBox card = new VBox(16, t, row);
        card.getStyleClass().add("card");
        return card;
    }

    private void loadUsers() {
        call(() -> api().getUsers(), list -> {
            users.getChildren().clear();
            for (JsonNode u : list) {
                Label l = new Label(u.path("username").asText() + "  ·  " + u.path("email").asText());
                l.getStyleClass().add("card-text");
                HBox row = new HBox(l);
                row.getStyleClass().add("table-row");
                users.getChildren().add(row);
            }
        });
    }
}
