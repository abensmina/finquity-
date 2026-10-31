package com.finquity.service;

import com.finquity.data.DataBase;
import com.finquity.model.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.TreeSet;

/*
 * Simule l'évolution du portefeuille d'une PME sur plusieurs années (Monte-Carlo).
 *
 * Java prépare les données (poids et historiques de prix alignés sur les mêmes dates),
 * le calcul est fait par src/analytics/monteCarlo.py via PythonRunner.
 */
public class SimulationService {

    private static final String SCRIPT = "monteCarlo.py";
    private static final int MIN_COMMON_DAYS = 60; // environ 3 mois de bourse

    private final DataBase db;
    private final PythonRunner python;

    public SimulationService(DataBase db, PythonRunner python) {
        this.db = Objects.requireNonNull(db, "La base de données est obligatoire.");
        this.python = Objects.requireNonNull(python, "PythonRunner est obligatoire.");
    }

    public SimulationResult simulate(int companyId, int years, int simulations) {
        return simulate(companyId, years, simulations, null);
    }

    // `seed` permet d'obtenir toujours le même résultat (utile pour les tests et les rapports).
    public SimulationResult simulate(int companyId, int years, int simulations, Long seed) {
        if (years < 1 || years > 30) {
            throw new IllegalArgumentException("La durée doit être comprise entre 1 et 30 ans.");
        }
        if (simulations < 100) {
            throw new IllegalArgumentException("Il faut au moins 100 simulations.");
        }

        portfolio p = db.getPortfolio(companyId);
        double total = p.getTotalValue();
        if (p.getNumberOfAssets() == 0 || total <= 0) {
            throw new IllegalArgumentException("Le portefeuille de l'entreprise est vide.");
        }

        // 1. Poids de chaque actif et son historique de prix
        List<Double> weights = new ArrayList<>();
        List<TreeMap<LocalDate, Double>> histories = new ArrayList<>();
        for (Map.Entry<Asset, Integer> entry : p.getAssets().entrySet()) {
            Asset asset = entry.getKey();
            TreeMap<LocalDate, Double> history = db.getPriceHistory(asset.getSymbol());
            if (history.size() < MIN_COMMON_DAYS) {
                throw new IllegalStateException("Historique de prix insuffisant pour " + asset.getSymbol()
                        + " : mets à jour les prix avec PriceService.");
            }
            weights.add(asset.getCurrentPrice() * entry.getValue() / total);
            histories.add(history);
        }

        // 2. On ne garde que les dates où tous les actifs ont un prix
        TreeSet<LocalDate> commonDates = new TreeSet<>(histories.get(0).keySet());
        for (TreeMap<LocalDate, Double> history : histories) {
            commonDates.retainAll(history.keySet());
        }
        if (commonDates.size() < MIN_COMMON_DAYS) {
            throw new IllegalStateException(
                    "Pas assez de dates communes entre les actifs (" + commonDates.size() + ").");
        }

        double[][] prices = new double[histories.size()][commonDates.size()];
        for (int i = 0; i < histories.size(); i++) {
            int j = 0;
            for (LocalDate date : commonDates) {
                prices[i][j++] = histories.get(i).get(date);
            }
        }

        // 3. Appel du script Python
        String input = "{"
                + "\"weights\":" + PythonRunner.toJson(weights.stream().mapToDouble(Double::doubleValue).toArray())
                + ",\"prices\":" + PythonRunner.toJson(prices)
                + ",\"initial_value\":" + total
                + ",\"years\":" + years
                + ",\"simulations\":" + simulations
                + (seed != null ? ",\"seed\":" + seed : "")
                + "}";
        String output = python.run(SCRIPT, input);

        // 4. Lecture du résultat
        return new SimulationResult(total,
                PythonRunner.readDoubleArray(output, "p5"),
                PythonRunner.readDoubleArray(output, "p50"),
                PythonRunner.readDoubleArray(output, "p95"),
                PythonRunner.readDouble(output, "final_mean"),
                PythonRunner.readDouble(output, "prob_loss"),
                PythonRunner.readDouble(output, "expected_annual_return"),
                PythonRunner.readDouble(output, "annual_volatility"));
    }
}
