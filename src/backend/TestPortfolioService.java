import java.io.File;
import java.util.Map;

public class TestPortfolioService {

    public static void main(String[] args) {
        String file = "finquity-test.db";
        new File(file).delete();

        try (DataBase db = new DataBase(file)) {
            portfolioService service = new portfolioService(db);

            // 1. Préparation : un utilisateur et un catalogue
            int id = db.createUser("lina", "lina@mail.com", "hash123").getId();
            db.saveAsset(new Asset("AAPL", "Apple", "Stock", "USD", 230.0));
            db.saveAsset(new Asset("MSFT", "Microsoft", "Stock", "USD", 420.0));

            // 2. Achats
            service.buyAsset(id, "AAPL", 10);
            service.buyAsset(id, "msft", 5);
            service.buyAsset(id, "AAPL", 2);
            System.out.println("Valeur : " + service.getTotalValue(id)); // 4860.0

            // 3. Répartition
            for (Map.Entry<String, Double> e : service.getAllocation(id).entrySet()) {
                System.out.printf("%s : %.1f %%%n", e.getKey(), e.getValue() * 100);
            }

            // 4. Vente
            service.sellAsset(id, "AAPL", 2);
            System.out.println("Après vente : " + service.getTotalValue(id)); // 4400.0

            // 5. Cas refusés
            testInvalide("actif inconnu", () -> service.buyAsset(id, "TSLA", 1));
            testInvalide("vente trop grande", () -> service.sellAsset(id, "MSFT", 100));
            testInvalide("quantité négative", () -> service.buyAsset(id, "AAPL", -3));
        }
    }

    private static void testInvalide(String cas, Runnable action) {
        try {
            action.run();
            System.out.println("ÉCHEC : " + cas + " aurait dû être refusé");
        } catch (IllegalArgumentException e) {
            System.out.println("OK : " + cas + " -> " + e.getMessage());
        }
    }
}