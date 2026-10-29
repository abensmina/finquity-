
public class TestPortfolio {

    public static void main(String[] args) {

        // 1. Création de deux objets représentant Apple
        Asset apple1 = new Asset(
            "AAPL", "Apple", "Stock", "USD", 230.0
        );

        Asset apple2 = new Asset(
            "AAPL", "Apple", "Stock", "USD", 230.0
        );

        // 2. Création du portefeuille
        portfolio p = new portfolio();

        // 3. Ajout de 10 actions Apple
        p.addAsset(apple1, 10);

        // 4. Ajout de 2 actions Apple supplémentaires
        p.addAsset(apple2, 2);

        // 5. Affichage des résultats
        System.out.println("Nombre de positions : "
            + p.getNumberOfAssets());

        System.out.println("Quantité détenue : "
            + p.getAssets().get(apple1));

        System.out.println("Valeur totale : "
            + p.getTotalValue());

        // 6. Retrait de 3 actions
        p.removeAsset(apple2, 3);

        System.out.println("Quantité après retrait : "
            + p.getAssets().get(apple1));

        System.out.println("Valeur après retrait : "
            + p.getTotalValue());
    }
}
