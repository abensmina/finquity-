package com.finquity.ui.view;

import com.fasterxml.jackson.databind.JsonNode;
import com.finquity.ui.Navigator;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/*
 * Simulation Monte-Carlo du portefeuille.
 * Le serveur Java lance le script Python (monteCarlo.py) et renvoie trois trajectoires :
 * pessimiste (5 %), médiane et optimiste (95 %).
 */
public class SimulationScreen extends Screen {

    private final TextField years = new TextField("5");
    private final TextField simulations = new TextField("5000");
    private final HBox kpis = new HBox(16);
    private final VBox chartBox = new VBox();

    public SimulationScreen(Navigator nav) {
        super(nav);
    }

    @Override
    protected String title() {
        return "Simulation";
    }

    @Override
    protected String subtitle() {
        return "Évolution possible du portefeuille (Monte-Carlo)";
    }

    @Override
    protected Node content() {
        Button run = primaryButton("Lancer la simulation");
        run.setOnAction(e -> run());
        HBox form = new HBox(16, field("Horizon (années)", years), field("Nombre de scénarios", simulations), run);
        form.setAlignment(Pos.BOTTOM_LEFT);
        Label note = new Label("Les calculs sont faits en Python à partir de l'historique des prix. "
                + "Il faut des placements dans le portefeuille et leurs prix mis à jour.");
        note.getStyleClass().add("card-text");
        note.setWrapText(true);
        VBox card = new VBox(16, form, note);
        card.getStyleClass().add("card");
        return new VBox(32, headline("Simulation ", "Monte-Carlo"), card, kpis, chartBox);
    }

    private void run() {
        clearMessages();
        int y;
        int n;
        try {
            y = (int) parseNumber(years, "Horizon");
            n = (int) parseNumber(simulations, "Nombre de scénarios");
        } catch (IllegalArgumentException ex) {
            showError(ex.getMessage());
            return;
        }
        showInfo("Simulation en cours…");
        call(() -> api().simulate(y, n), result -> {
            clearMessages();
            show(result);
        });
    }

    private void show(JsonNode r) {
        kpis.getChildren().setAll(
                kpi("Valeur de départ", EUROS.format(r.path("initialValue").asDouble()), "Portefeuille actuel", null),
                kpi("Valeur moyenne finale", EUROS.format(r.path("finalMean").asDouble()), "Moyenne des scénarios", "gold"),
                kpi("Probabilité de perte", percent(r.path("probabilityOfLoss").asDouble()), "À l'horizon choisi", null),
                kpi("Rendement annuel attendu", percent(r.path("expectedAnnualReturn").asDouble()),
                        "Volatilité " + percent(r.path("annualVolatility").asDouble()), "green"));

        NumberAxis x = new NumberAxis();
        x.setLabel("Années");
        NumberAxis yAxis = new NumberAxis();
        yAxis.setForceZeroInRange(false);
        LineChart<Number, Number> chart = new LineChart<>(x, yAxis);
        chart.getData().add(series("Pessimiste (5 %)", r.path("pessimistic"), r.path("years").asInt(5)));
        chart.getData().add(series("Médiane", r.path("median"), r.path("years").asInt(5)));
        chart.getData().add(series("Optimiste (95 %)", r.path("optimistic"), r.path("years").asInt(5)));
        chart.setCreateSymbols(false);
        chart.setAnimated(false);
        chart.setPrefHeight(380);
        chart.getStyleClass().add("chart-card");
        chartBox.getChildren().setAll(sectionTitle("Trajectoires"), chart);
    }

    private XYChart.Series<Number, Number> series(String name, JsonNode values, int years) {
        XYChart.Series<Number, Number> s = new XYChart.Series<>();
        s.setName(name);
        int steps = values.size();
        for (int i = 0; i < steps; i++) {
            double t = steps > 1 ? (double) i * years / (steps - 1) : 0;
            s.getData().add(new XYChart.Data<>(t, values.get(i).asDouble()));
        }
        return s;
    }
}
