import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/*
 * Tient à jour les prix des actifs du catalogue :
 * récupère l'historique auprès d'un fournisseur (Twelve Data),
 * l'enregistre dans price_history et met à jour le prix courant de l'actif.
 */
public class PriceService {

    // Environ 3 ans de jours de bourse : assez pour la volatilité et l'optimisation.
    public static final int DEFAULT_DAYS = 750;

    private final DataBase db;
    private final PriceProvider provider;

    public PriceService(DataBase db, PriceProvider provider) {
        this.db = Objects.requireNonNull(db, "La base de données est obligatoire.");
        this.provider = Objects.requireNonNull(provider, "Le fournisseur de prix est obligatoire.");
    }

    /*
     * Met à jour un actif : historique + prix courant (= dernière clôture).
     * L'actif doit déjà exister dans le catalogue.
     */
    public void updatePrices(String symbol, int days) {
        Asset asset = db.findAssetBySymbol(symbol).orElseThrow(
                () -> new IllegalArgumentException("Actif inconnu : " + symbol));

        TreeMap<LocalDate, Double> closes = provider.fetchDailyCloses(asset.getSymbol(), days);
        db.savePriceHistory(asset.getSymbol(), closes);

        asset.setCurrentPrice(closes.lastEntry().getValue());
        db.saveAsset(asset);
    }

    /*
     * Met à jour tout le catalogue. Une erreur sur un actif n'arrête pas les autres :
     * on renvoie, pour chaque symbole, "OK" ou le message d'erreur.
     */
    public Map<String, String> updateAllPrices(int days) {
        Map<String, String> report = new LinkedHashMap<>();
        for (Asset asset : db.getAllAssets()) {
            try {
                updatePrices(asset.getSymbol(), days);
                report.put(asset.getSymbol(), "OK");
            } catch (RuntimeException e) {
                report.put(asset.getSymbol(), e.getMessage());
            }
        }
        return report;
    }

    // Historique enregistré, utilisé ensuite par les analyses (volatilité, optimisation...).
    public TreeMap<LocalDate, Double> getHistory(String symbol) {
        return db.getPriceHistory(symbol);
    }
}
