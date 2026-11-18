package com.finquity.ui.view;

import com.fasterxml.jackson.databind.JsonNode;
import com.finquity.api.Dto.AssetRequest;
import com.finquity.api.Dto.PortfolioView;
import com.finquity.api.Dto.PositionView;
import com.finquity.ui.Navigator;
import java.util.Locale;
import javafx.collections.FXCollections;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.chart.PieChart;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/*
 * Portefeuille : positions détenues, répartition, achat / vente (simulés)
 * et ajout d'un placement au catalogue.
 */
public class PortfolioScreen extends Screen {

    private final Label total = new Label("—");
    private final TableView<PositionRow> table = new TableView<>();
    private final PieChart pie = new PieChart();
    private final ComboBox<String> symbol = new ComboBox<>();
    private final TextField quantity = new TextField("1");

    private final TextField newSymbol = new TextField();
    private final TextField newName = new TextField();
    private final ComboBox<String> newType = new ComboBox<>(FXCollections.observableArrayList(
            "MONETAIRE", "OBLIGATION", "ETF", "ACTION"));
    private final TextField newPrice = new TextField();

    public PortfolioScreen(Navigator nav) {
        super(nav);
    }

    @Override
    protected String title() {
        return "Portefeuille";
    }

    @Override
    protected String subtitle() {
        return "Vos placements (achats et ventes simulés)";
    }

    @Override
    protected Node content() {
        buildTable();
        pie.setLabelsVisible(true);
        pie.setLegendVisible(false);
        pie.setPrefSize(360, 300);
        pie.setAnimated(false);
        pie.getStyleClass().add("chart-card");
        total.getStyleClass().addAll("kpi-val", "gold");
        Label l = new Label("VALEUR TOTALE");
        l.getStyleClass().add("kpi-label");
        VBox totalCard = new VBox(8, l, total);
        totalCard.getStyleClass().add("card");

        HBox top = new HBox(16, new VBox(16, totalCard, table), pie);
        HBox.setHgrow(top.getChildren().get(0), Priority.ALWAYS);

        loadAssets();
        loadPortfolio();
        return new VBox(32, headline("Votre ", "portefeuille"), top, tradeCard(), addAssetCard());
    }

    // ---------- Tableau des positions ----------

    // Une ligne du tableau (JavaFX lit les getters par leur nom).
    public static class PositionRow {
        private final PositionView p;

        PositionRow(PositionView p) {
            this.p = p;
        }

        public String getSymbol() {
            return p.symbol();
        }

        public String getName() {
            return p.name();
        }

        public String getType() {
            return p.type();
        }

        public int getQuantity() {
            return p.quantity();
        }

        public String getPrice() {
            return EUROS.format(p.price());
        }

        public String getValue() {
            return EUROS.format(p.value());
        }

        public String getWeight() {
            return String.format(Locale.FRANCE, "%.1f %%", p.weight() * 100);
        }
    }

    private void buildTable() {
        String[][] cols = {{"Symbole", "symbol"}, {"Nom", "name"}, {"Type", "type"}, {"Quantité", "quantity"},
            {"Prix", "price"}, {"Valeur", "value"}, {"Poids", "weight"}};
        for (String[] c : cols) {
            TableColumn<PositionRow, Object> col = new TableColumn<>(c[0]);
            col.setCellValueFactory(new PropertyValueFactory<>(c[1]));
            table.getColumns().add(col);
        }
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(new Label("Aucun placement pour l'instant."));
        table.setPrefHeight(240);
    }

    private void showPortfolio(PortfolioView view) {
        total.setText(EUROS.format(view.totalValue()));
        table.getItems().setAll(view.positions().stream().map(PositionRow::new).toList());
        pie.getData().clear();
        for (PositionView p : view.positions()) {
            pie.getData().add(new PieChart.Data(p.symbol(), p.value()));
        }
    }

    private void loadPortfolio() {
        call(() -> api().getPortfolio(), this::showPortfolio);
    }

    private void loadAssets() {
        call(() -> api().getAssets(), assets -> {
            symbol.getItems().clear();
            for (JsonNode a : assets) {
                symbol.getItems().add(a.path("symbol").asText());
            }
            if (!symbol.getItems().isEmpty()) {
                symbol.getSelectionModel().selectFirst();
            }
        });
    }

    // ---------- Acheter / vendre ----------

    private VBox tradeCard() {
        Label t = new Label("Acheter ou vendre");
        t.getStyleClass().add("card-title");
        symbol.setPromptText("Placement");
        symbol.setPrefWidth(200);
        Label ls = new Label("PLACEMENT");
        ls.getStyleClass().add("field-label");
        VBox symbolBox = new VBox(6, ls, symbol);
        Button buy = primaryButton("Acheter");
        Button sell = secondaryButton("Vendre");
        buy.setOnAction(e -> trade(true));
        sell.setOnAction(e -> trade(false));
        Button prices = secondaryButton("Mettre à jour les prix");
        prices.setOnAction(e -> updatePrices());
        HBox row = new HBox(16, symbolBox, field("Quantité", quantity), buy, sell, prices);
        row.setAlignment(Pos.BOTTOM_LEFT);
        VBox card = new VBox(16, t, row);
        card.getStyleClass().add("card");
        return card;
    }

    private void trade(boolean buying) {
        clearMessages();
        String s = symbol.getValue();
        if (s == null) {
            showError("Choisissez un placement (ajoutez-en un au catalogue si la liste est vide).");
            return;
        }
        int q;
        try {
            q = (int) parseNumber(quantity, "Quantité");
        } catch (IllegalArgumentException ex) {
            showError(ex.getMessage());
            return;
        }
        call(() -> buying ? api().buy(s, q) : api().sell(s, q), view -> {
            showPortfolio(view);
            showInfo((buying ? "Achat" : "Vente") + " de " + q + " × " + s + " enregistré(e).");
        });
    }

    // L'historique des prix sert à la simulation. Twelve Data limite le nombre d'appels : cela peut prendre du temps.
    private void updatePrices() {
        showInfo("Mise à jour des prix en cours… (environ 8 secondes par placement)");
        call(() -> api().updateAllPrices(), result -> {
            StringBuilder sb = new StringBuilder("Prix mis à jour :");
            result.fields().forEachRemaining(e -> sb.append("  ").append(e.getKey()).append(" → ")
                    .append(e.getValue().asText()).append(" ;"));
            showInfo(sb.toString());
            loadPortfolio();
        });
    }

    // ---------- Ajouter un placement au catalogue ----------

    private VBox addAssetCard() {
        Label t = new Label("Ajouter un placement au catalogue");
        t.getStyleClass().add("card-title");
        newSymbol.setPromptText("ex. CW8");
        newName.setPromptText("ex. ETF MSCI World");
        newPrice.setPromptText("ex. 520,40");
        newType.getSelectionModel().select("ETF");
        Label lt = new Label("TYPE");
        lt.getStyleClass().add("field-label");
        Button add = secondaryButton("Ajouter");
        add.setOnAction(e -> addAsset());
        HBox row = new HBox(16, field("Symbole", newSymbol), field("Nom", newName), new VBox(6, lt, newType),
                field("Prix actuel (€)", newPrice), add);
        row.setAlignment(Pos.BOTTOM_LEFT);
        VBox card = new VBox(16, t, row);
        card.getStyleClass().add("card");
        return card;
    }

    private void addAsset() {
        clearMessages();
        AssetRequest request;
        try {
            request = new AssetRequest(newSymbol.getText().trim().toUpperCase(), newName.getText().trim(),
                    newType.getValue(), "EUR", parseNumber(newPrice, "Prix actuel"));
        } catch (IllegalArgumentException ex) {
            showError(ex.getMessage());
            return;
        }
        call(() -> api().addAsset(request), a -> {
            showInfo("Placement " + a.path("symbol").asText() + " ajouté au catalogue.");
            loadAssets();
        });
    }
}
