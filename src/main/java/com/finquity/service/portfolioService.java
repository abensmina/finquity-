package com.finquity.service;

import com.finquity.data.DataBase;
import com.finquity.model.*;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public class portfolioService {

    private final DataBase db;

    public portfolioService(DataBase db) {
        this.db = Objects.requireNonNull(db, "La base de données est obligatoire.");
    }

    public portfolio getPortfolio(int companyId) {
        return db.getPortfolio(companyId);
    }

    public void buyAsset(int companyId, String symbol, int quantity) {
        Asset asset = getAssetFromCatalog(symbol);
        portfolio p = db.getPortfolio(companyId);
        p.addAsset(asset, quantity);
        db.savePortfolio(companyId, p);
    }

    public void sellAsset(int companyId, String symbol, int quantity) {
        Asset asset = getAssetFromCatalog(symbol);
        portfolio p = db.getPortfolio(companyId);
        p.removeAsset(asset, quantity);
        db.savePortfolio(companyId, p);
    }

    public double getTotalValue(int companyId) {
        return db.getPortfolio(companyId).getTotalValue();
    }

    public Map<String, Double> getAllocation(int companyId) {
        portfolio p = db.getPortfolio(companyId);
        double total = p.getTotalValue();
        Map<String, Double> allocation = new LinkedHashMap<>();
        if (total == 0) {
            return allocation;
        }
        for (Map.Entry<Asset, Integer> entry : p.getAssets().entrySet()) {
            double positionValue = entry.getKey().getCurrentPrice() * entry.getValue();
            allocation.put(entry.getKey().getSymbol(), positionValue / total);
        }
        return Collections.unmodifiableMap(allocation);
    }

    private Asset getAssetFromCatalog(String symbol) {
        return db.findAssetBySymbol(symbol).orElseThrow(
                () -> new IllegalArgumentException("Actif inconnu : " + symbol));
    }
}
