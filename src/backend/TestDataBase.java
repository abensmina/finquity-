import java.io.File;

public class TestDataBase {

    public static void main(String[] args) {
        String file = "finquity-test.db";
        new File(file).delete(); // repart d'une base vide à chaque test

        int companyId;
        int linaId;

        // 1. Première session : on écrit
        try (DataBase db = new DataBase(file)) {
            Company pme = db.createCompany("Boulangerie Martin", "123456789", "Agroalimentaire");
            companyId = pme.getId();
            System.out.println("Créée : " + pme);

            user lina = db.createUser(companyId, "lina", "Lina@Mail.com", "hash123");
            linaId = lina.getId();
            System.out.println("Créé : " + lina);

            testInvalide("SIREN en double",
                    () -> db.createCompany("Copie", "123456789", null));
            testInvalide("SIREN invalide",
                    () -> db.createCompany("Mauvaise", "12AB", null));
            testInvalide("email en double",
                    () -> db.createUser(companyId, "autre", "lina@mail.com", "hash456"));
            testInvalide("entreprise inexistante",
                    () -> db.createUser(999, "x", "x@mail.com", "h"));

            // Comptes annuels sur deux ans
            db.saveFinancialStatement(new FinancialStatement(companyId, 2024,
                    800_000, 40_000, 760_000, 200_000, 150_000, 300_000, 180_000, 90_000));
            db.saveFinancialStatement(new FinancialStatement(companyId, 2025,
                    950_000, 65_000, 885_000, 265_000, 120_000, 380_000, 200_000, 150_000));

            // Portefeuille de l'entreprise
            portfolio p = db.getPortfolio(companyId);
            p.addAsset(new Asset("CSH2", "ETF monétaire", "ETF", "EUR", 100.0), 300);
            p.addAsset(new Asset("OBLI", "ETF obligataire", "ETF", "EUR", 50.0), 200);
            db.savePortfolio(companyId, p);
        }

        // 2. Deuxième session : on relit depuis le fichier
        try (DataBase db = new DataBase(file)) {
            System.out.println("Retrouvée : " + db.findCompanyBySiren("123456789").isPresent());
            System.out.println("Utilisateurs : " + db.getUsersOfCompany(companyId).size()); // 1
            System.out.println("Exercices : " + db.getFinancialStatements(companyId).size()); // 2
            System.out.println("Dernier exercice : "
                    + db.getLatestFinancialStatement(companyId).orElseThrow().getYear()); // 2025
            System.out.println("Valeur du portefeuille : "
                    + db.getPortfolio(companyId).getTotalValue()); // 40000.0

            // Supprimer l'utilisateur ne touche pas au portefeuille
            db.deleteUser(linaId);
            System.out.println("Portefeuille après suppression de l'utilisateur : "
                    + db.getPortfolio(companyId).getNumberOfAssets()); // 2

            // Supprimer l'entreprise supprime tout le reste
            db.deleteCompany(companyId);
            System.out.println("Comptes après suppression de l'entreprise : "
                    + db.getFinancialStatements(companyId).size()); // 0
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
