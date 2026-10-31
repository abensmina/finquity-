import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public class portfolioService {

    private final DataBase db;

    // Le service reçoit la base au lieu de la créer : on peut lui donner
    // une base de test ou la vraie base.
    public portfolioService(DataBase db) {
        this.db = Objects.requireNonNull(db, "La base de données est obligatoire.");
    }

    public portfolio getPortfolio(int userId) {
        return db.getPortfolio(userId);
    }

    // Ajoute une quantité d'un actif du catalogue au portefeuille.
    public void buyAsset(int userId, String symbol, int quantity) {
        Asset asset = getAssetFromCatalog(symbol);
        portfolio p = db.getPortfolio(userId);
        p.addAsset(asset, quantity);
        db.savePortfolio(userId, p);
    }

    // Retire une quantité ; portfolio refuse si on vend plus que ce qu'on a.
    public void sellAsset(int userId, String symbol, int quantity) {
        Asset asset = getAssetFromCatalog(symbol);
        portfolio p = db.getPortfolio(userId);
        p.removeAsset(asset, quantity);
        db.savePortfolio(userId, p);
    }

    public double getTotalValue(int userId) {
        return db.getPortfolio(userId).getTotalValue();
    }

    /*
     * Poids de chaque actif dans la valeur totale, entre 0 et 1.
     * Exemple : AAPL -> 0.57, MSFT -> 0.43
     */
    public Map<String, Double> getAllocation(int userId) {
        portfolio p = db.getPortfolio(userId);
        double total = p.getTotalValue();
        Map<String, Double> allocation = new LinkedHashMap<>();

        // Portefeuille vide ou sans valeur : pas de répartition possible.
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