package com.finquity.service;

import java.time.LocalDate;
import java.util.TreeMap;

/*
 * Une source de prix (Twelve Data aujourd'hui, une autre demain).
 * PriceService ne connaît que cette interface : changer de fournisseur
 * revient à écrire une nouvelle classe qui l'implémente.
 */
public interface PriceProvider {

    // Prix de clôture journaliers des `days` derniers jours de bourse, triés par date.
    TreeMap<LocalDate, Double> fetchDailyCloses(String symbol, int days);
}
