package com.finquity.ui.view;

import com.fasterxml.jackson.databind.JsonNode;
import com.finquity.ui.Navigator;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/*
 * Analyse financière : affiche les ratios et la note CALCULÉS PAR LE SERVEUR (AnalysisService).
 * Aucun calcul n'est refait ici, pour avoir une seule source de vérité.
 * Les couleurs reprennent les mêmes seuils que AnalysisService.
 */
public class AnalysisScreen extends Screen {

    private final VBox body = new VBox(32);

    public AnalysisScreen(Navigator nav) {
        super(nav);
    }

    @Override
    protected String title() {
        return "Analyse financière";
    }

    @Override
    protected String subtitle() {
        return "Ratios, note de santé et profil de risque";
    }

    @Override
    protected Node content() {
        call(() -> api().getStatements(), statements -> {
            if (statements.isEmpty()) {
                Label none = new Label("Importez d'abord vos comptes annuels.");
                none.getStyleClass().add("card-text");
                Button go = primaryButton("Importer mes comptes");
                go.setOnAction(e -> nav.show(new ImportScreen(nav)));
                body.getChildren().setAll(none, go);
            } else {
                call(() -> api().getDiagnosis(), d -> show(d, statements));
            }
        });
        return new VBox(32, headline("Analyse ", "financière"), body);
    }

    private void show(JsonNode d, JsonNode statements) {
        body.getChildren().setAll(scoreCard(d), sectionTitle("Ratios de l'exercice " + d.path("year").asText()),
                ratios(d), alerts(d), sectionTitle("Évolution"), chart(statements));
    }

    private VBox scoreCard(JsonNode d) {
        int score = d.path("score").asInt();
        String verdict = score >= 70 ? "Sain" : score >= 45 ? "Vigilance" : "Fragile";
        Label value = new Label(score + " / 100");
        value.getStyleClass().addAll("kpi-val", "gold", "big");
        Label badge = new Label(verdict.toUpperCase());
        badge.getStyleClass().add(score >= 70 ? "badge-ok" : score >= 45 ? "badge-warn" : "badge-bad");
        ProgressBar bar = new ProgressBar(score / 100.0);
        bar.setMaxWidth(Double.MAX_VALUE);
        bar.getStyleClass().add("score-bar");
        Label profile = new Label("Profil " + profileLabel(d.path("profile").asText())
                + "  ·  montant plaçable " + EUROS.format(d.path("investableAmount").asDouble())
                + "  ·  réserve " + EUROS.format(d.path("reserve").asDouble()));
        profile.getStyleClass().add("card-text");
        Label l = new Label("NOTE DE SANTÉ FINANCIÈRE");
        l.getStyleClass().add("kpi-label");
        HBox top = new HBox(16, value, badge);
        top.setAlignment(Pos.CENTER_LEFT);
        VBox card = new VBox(12, l, top, bar, profile);
        card.getStyleClass().add("card");
        return card;
    }

    private VBox ratios(JsonNode d) {
        double de = d.path("debtToEquity").asDouble();
        double cr = d.path("currentRatio").asDouble();
        double nm = d.path("netMargin").asDouble();
        double roe = d.path("returnOnEquity").asDouble();
        double g = d.path("revenueGrowth").asDouble(Double.NaN);
        return new VBox(
                ratioRow("Endettement", "Dettes / capitaux propres", ratio(de),
                        level(de < 0.5, de < 2.0)),
                ratioRow("Liquidité générale", "Actif circulant / passif circulant", ratio(cr),
                        level(cr >= 1.5, cr >= 1.0)),
                ratioRow("Marge nette", "Résultat net / chiffre d'affaires", percent(nm),
                        level(nm >= 0.10, nm > 0)),
                ratioRow("Rentabilité des capitaux propres", "Résultat net / capitaux propres", percent(roe),
                        level(roe >= 0.15, roe > 0)),
                ratioRow("Croissance du chiffre d'affaires", "Évolution sur un an", percent(g),
                        Double.isNaN(g) ? "neutral" : level(g >= 0.05, g >= 0)));
    }

    // "ok" si bon, "warn" si moyen, "bad" sinon.
    private String level(boolean good, boolean average) {
        return good ? "ok" : average ? "warn" : "bad";
    }

    private HBox ratioRow(String name, String formula, String value, String level) {
        Label n = new Label(name);
        n.getStyleClass().add("ratio-name");
        Label f = new Label(formula);
        f.getStyleClass().add("ratio-formula");
        VBox left = new VBox(2, n, f);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Label v = new Label(value);
        v.getStyleClass().addAll("ratio-val", "val-" + level);
        String text = switch (level) {
            case "ok" -> "BON";
            case "warn" -> "MOYEN";
            case "bad" -> "FAIBLE";
            default -> "—";
        };
        Label badge = new Label(text);
        badge.getStyleClass().add("badge-" + level);
        badge.setMinWidth(80);
        badge.setAlignment(Pos.CENTER);
        HBox row = new HBox(20, left, spacer, v, badge);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("ratio-row");
        return row;
    }

    private VBox alerts(JsonNode d) {
        VBox box = new VBox(8);
        for (JsonNode a : d.path("alerts")) {
            Label l = new Label("•  " + a.asText());
            l.getStyleClass().add("alert");
            l.setWrapText(true);
            box.getChildren().add(l);
        }
        if (box.getChildren().isEmpty()) {
            Label l = new Label("Aucune alerte sur cet exercice.");
            l.getStyleClass().add("card-text");
            box.getChildren().add(l);
        }
        box.getChildren().add(0, sectionTitle("Points d'attention"));
        return box;
    }

    // Chiffre d'affaires et résultat net par année.
    private BarChart<String, Number> chart(JsonNode statements) {
        BarChart<String, Number> chart = new BarChart<>(new CategoryAxis(), new NumberAxis());
        XYChart.Series<String, Number> ca = new XYChart.Series<>();
        ca.setName("Chiffre d'affaires");
        XYChart.Series<String, Number> rn = new XYChart.Series<>();
        rn.setName("Résultat net");
        for (JsonNode s : statements) {
            String y = s.path("year").asText();
            ca.getData().add(new XYChart.Data<>(y, s.path("revenue").asDouble()));
            rn.getData().add(new XYChart.Data<>(y, s.path("netIncome").asDouble()));
        }
        chart.getData().add(ca);
        chart.getData().add(rn);
        chart.setAnimated(false);
        chart.setPrefHeight(300);
        chart.getStyleClass().add("chart-card");
        return chart;
    }
}
