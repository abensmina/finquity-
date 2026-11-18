package com.finquity.ui.view;

import com.fasterxml.jackson.databind.JsonNode;
import com.finquity.api.Dto.StatementRequest;
import com.finquity.ui.Navigator;
import java.io.File;
import java.time.Year;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

/*
 * Import des comptes annuels :
 *  - soit un fichier Excel (le serveur le lit avec Apache POI, tout ou rien) ;
 *  - soit la saisie d'une année à la main.
 */
public class ImportScreen extends Screen {

    private final TextField year = new TextField(String.valueOf(Year.now().getValue() - 1));
    private final TextField revenue = new TextField();
    private final TextField netIncome = new TextField();
    private final TextField expenses = new TextField();
    private final TextField equity = new TextField();
    private final TextField debt = new TextField();
    private final TextField currentAssets = new TextField();
    private final TextField currentLiabilities = new TextField();
    private final TextField cash = new TextField();
    private final VBox history = new VBox(8);

    public ImportScreen(Navigator nav) {
        super(nav);
    }

    @Override
    protected String title() {
        return "Import des comptes";
    }

    @Override
    protected String subtitle() {
        return "Fichier Excel ou saisie manuelle";
    }

    @Override
    protected Node content() {
        loadHistory();
        return new VBox(32, headline("Vos comptes ", "annuels"), excelCard(), manualCard(),
                sectionTitle("Années enregistrées"), history);
    }

    // ---------- Import Excel ----------

    private VBox excelCard() {
        Label t = new Label("Importer un fichier Excel");
        t.getStyleClass().add("card-title");
        Label d = new Label("Une ligne par année, colonnes A à I : année, chiffre d'affaires, résultat net, "
                + "charges annuelles, capitaux propres, dettes, actif circulant, passif circulant, trésorerie. "
                + "Si une case est fausse, rien n'est enregistré et le message indique la ligne et la colonne.");
        d.getStyleClass().add("card-text");
        d.setWrapText(true);
        Button choose = primaryButton("Choisir un fichier .xlsx");
        choose.setOnAction(e -> chooseFile(choose));
        VBox card = new VBox(14, t, d, choose);
        card.getStyleClass().add("card");
        return card;
    }

    private void chooseFile(Button owner) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Choisir le fichier des comptes annuels");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Fichier Excel", "*.xlsx"));
        File file = chooser.showOpenDialog(owner.getScene().getWindow());
        if (file == null) {
            return;
        }
        clearMessages();
        call(() -> api().importExcel(file.toPath()), imported -> {
            showInfo(imported.size() + " année(s) importée(s) depuis " + file.getName() + ".");
            loadHistory();
        });
    }

    // ---------- Saisie manuelle ----------

    private VBox manualCard() {
        Label t = new Label("Saisir une année à la main");
        t.getStyleClass().add("card-title");
        TextField[] inputs = {year, revenue, netIncome, expenses, equity, debt, currentAssets, currentLiabilities, cash};
        String[] labels = {"Année", "Chiffre d'affaires (€)", "Résultat net (€)", "Charges annuelles (€)",
            "Capitaux propres (€)", "Dettes financières (€)", "Actif circulant (€)",
            "Passif circulant (€)", "Trésorerie (€)"};
        GridPane grid = new GridPane();
        grid.setHgap(16);
        grid.setVgap(14);
        for (int i = 0; i < inputs.length; i++) {
            VBox f = field(labels[i], inputs[i]);
            GridPane.setHgrow(f, Priority.ALWAYS);
            grid.add(f, i % 3, i / 3);
        }
        Button save = primaryButton("Enregistrer l'année");
        save.setOnAction(e -> saveManual());
        VBox card = new VBox(18, t, grid, save);
        card.getStyleClass().add("card");
        return card;
    }

    private void saveManual() {
        clearMessages();
        StatementRequest request;
        try {
            request = new StatementRequest((int) parseNumber(year, "Année"),
                    parseNumber(revenue, "Chiffre d'affaires"), parseNumber(netIncome, "Résultat net"),
                    parseNumber(expenses, "Charges annuelles"), parseNumber(equity, "Capitaux propres"),
                    parseNumber(debt, "Dettes financières"), parseNumber(currentAssets, "Actif circulant"),
                    parseNumber(currentLiabilities, "Passif circulant"), parseNumber(cash, "Trésorerie"));
        } catch (IllegalArgumentException ex) {
            showError(ex.getMessage());
            return;
        }
        call(() -> api().saveStatement(request), saved -> {
            showInfo("Comptes " + saved.path("year").asText() + " enregistrés.");
            loadHistory();
        });
    }

    // ---------- Années déjà enregistrées ----------

    private void loadHistory() {
        call(() -> api().getStatements(), statements -> {
            history.getChildren().clear();
            if (statements.isEmpty()) {
                Label none = new Label("Aucune année pour l'instant.");
                none.getStyleClass().add("card-text");
                history.getChildren().add(none);
                return;
            }
            for (JsonNode s : statements) {
                history.getChildren().add(historyRow(s));
            }
            Button go = primaryButton("Voir l'analyse");
            go.setOnAction(e -> nav.show(new AnalysisScreen(nav)));
            history.getChildren().add(go);
        });
    }

    private HBox historyRow(JsonNode s) {
        Label y = new Label(s.path("year").asText());
        y.getStyleClass().add("row-year");
        Label ca = new Label("CA " + EUROS.format(s.path("revenue").asDouble()));
        Label rn = new Label("Résultat " + EUROS.format(s.path("netIncome").asDouble()));
        Label tr = new Label("Trésorerie " + EUROS.format(s.path("cash").asDouble()));
        for (Label l : new Label[] {ca, rn, tr}) {
            l.getStyleClass().add("card-text");
            l.setPrefWidth(220);
        }
        HBox row = new HBox(24, y, ca, rn, tr);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("table-row");
        return row;
    }
}
