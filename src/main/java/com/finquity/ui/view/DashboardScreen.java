package com.finquity.ui.view;

import com.fasterxml.jackson.databind.JsonNode;
import com.finquity.ui.Navigator;
import java.util.function.Function;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

// Tableau de bord : la note, le profil et le montant plaçable (calculés par le serveur), puis les modules.
public class DashboardScreen extends Screen {

    private final VBox header = new VBox(6);
    private final HBox kpis = new HBox(16);
    private final VBox body = new VBox(24);

    public DashboardScreen(Navigator nav) {
        super(nav);
    }

    @Override
    protected String title() {
        return "Tableau de bord";
    }

    @Override
    protected String subtitle() {
        return "Vue d'ensemble de votre entreprise";
    }

    @Override
    protected Node content() {
        load();
        return new VBox(40, header, kpis, body);
    }

    private void load() {
        call(() -> api().getCompany(), c -> {
            Label meta = new Label("SIREN " + c.path("siren").asText() + "  ·  " + c.path("sector").asText(""));
            meta.getStyleClass().add("company-meta");
            header.getChildren().setAll(headline(c.path("name").asText() + " ", "· Diagnostic"), meta);
        });
        call(() -> api().getStatements(), statements -> {
            if (statements.isEmpty()) {
                showNoData();
            } else {
                call(() -> api().getDiagnosis(), this::showDiagnosis);
            }
        });
    }

    private void showNoData() {
        Label sub = new Label("Importez vos comptes annuels (fichier Excel ou saisie manuelle) "
                + "pour obtenir votre note de santé financière et le montant que vous pouvez placer.");
        sub.getStyleClass().add("no-data-sub");
        sub.setWrapText(true);
        sub.setMaxWidth(520);
        Button go = primaryButton("Importer mes comptes");
        go.setOnAction(e -> nav.show(new ImportScreen(nav)));
        VBox box = new VBox(16, headline("Aucune donnée ", "pour l'instant"), sub, go);
        box.setAlignment(Pos.CENTER);
        box.getStyleClass().add("no-data");
        body.getChildren().setAll(box);
    }

    private void showDiagnosis(JsonNode d) {
        int score = d.path("score").asInt();
        kpis.getChildren().setAll(
                kpi("Note de santé", score + " / 100", "Exercice " + d.path("year").asText(), "gold"),
                kpi("Profil de risque", profileLabel(d.path("profile").asText()), "Déduit de la note", null),
                kpi("Montant plaçable", EUROS.format(d.path("investableAmount").asDouble()),
                        "Après réserve de sécurité", "green"),
                kpi("Réserve à garder", EUROS.format(d.path("reserve").asDouble()), "Mois de charges gardés", null));

        GridPane modules = new GridPane();
        modules.setHgap(16);
        modules.setVgap(16);
        ColumnConstraints half = new ColumnConstraints();
        half.setPercentWidth(50);
        modules.getColumnConstraints().addAll(half, half);
        modules.add(module("Analyse financière", "Ratios de solvabilité, liquidité, rentabilité et croissance.",
                AnalysisScreen::new, true), 0, 0);
        modules.add(module("Portefeuille", "Acheter, vendre et suivre la répartition de vos placements.",
                PortfolioScreen::new, true), 1, 0);
        modules.add(module("Simulation", "Évolution possible du portefeuille (méthode de Monte-Carlo).",
                SimulationScreen::new, true), 0, 1);
        modules.add(module("Rapport PDF", "Un document récapitulatif à télécharger et à partager.",
                ReportScreen::new, true), 1, 1);

        body.getChildren().setAll(sectionTitle("Modules"), modules);
    }

    private VBox module(String name, String desc, Function<Navigator, Screen> target, boolean ready) {
        Label n = new Label(name);
        n.getStyleClass().add("module-name");
        Label dsc = new Label(desc);
        dsc.getStyleClass().add("module-desc");
        dsc.setWrapText(true);
        Label status = new Label(ready ? "DISPONIBLE" : "BIENTÔT");
        status.getStyleClass().add(ready ? "status-ready" : "status-pending");
        VBox card = new VBox(12, n, dsc, status);
        card.getStyleClass().addAll("card", "module-card");
        card.setMaxWidth(Double.MAX_VALUE);
        GridPane.setHgrow(card, Priority.ALWAYS);
        card.setOnMouseClicked(e -> nav.show(target.apply(nav)));
        return card;
    }
}
