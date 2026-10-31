package com.finquity.model;

/*
 * Les chiffres clés d'une PME pour un exercice (une année).
 * Tous les montants sont en euros.
 * L'objet est immuable : pour corriger une année, on en crée un nouveau.
 */
public class FinancialStatement {
    private final int companyId;
    private final int year;

    // Compte de résultat
    private final double revenue;            // chiffre d'affaires
    private final double netIncome;          // résultat net (peut être négatif)
    private final double annualExpenses;     // charges totales de l'année

    // Bilan
    private final double equity;             // capitaux propres (peuvent être négatifs)
    private final double totalDebt;          // dettes financières
    private final double currentAssets;      // actif circulant
    private final double currentLiabilities; // passif circulant (dettes à moins d'un an)
    private final double cash;               // trésorerie disponible

    public FinancialStatement(int companyId, int year,
                              double revenue, double netIncome, double annualExpenses,
                              double equity, double totalDebt,
                              double currentAssets, double currentLiabilities,
                              double cash) {
        if (companyId <= 0) {
            throw new IllegalArgumentException("L'identifiant de l'entreprise est invalide.");
        }
        if (year < 1900 || year > 2100) {
            throw new IllegalArgumentException("L'année de l'exercice est invalide.");
        }

        this.companyId = companyId;
        this.year = year;
        this.revenue = requirePositiveOrZero(revenue, "Le chiffre d'affaires");
        this.netIncome = requireFinite(netIncome, "Le résultat net");
        this.annualExpenses = requirePositiveOrZero(annualExpenses, "Les charges annuelles");
        this.equity = requireFinite(equity, "Les capitaux propres");
        this.totalDebt = requirePositiveOrZero(totalDebt, "Les dettes");
        this.currentAssets = requirePositiveOrZero(currentAssets, "L'actif circulant");
        this.currentLiabilities = requirePositiveOrZero(currentLiabilities, "Le passif circulant");
        this.cash = requirePositiveOrZero(cash, "La trésorerie");
    }

    public int getCompanyId() {
        return companyId;
    }

    public int getYear() {
        return year;
    }

    public double getRevenue() {
        return revenue;
    }

    public double getNetIncome() {
        return netIncome;
    }

    public double getAnnualExpenses() {
        return annualExpenses;
    }

    public double getEquity() {
        return equity;
    }

    public double getTotalDebt() {
        return totalDebt;
    }

    public double getCurrentAssets() {
        return currentAssets;
    }

    public double getCurrentLiabilities() {
        return currentLiabilities;
    }

    public double getCash() {
        return cash;
    }

    private static double requireFinite(double value, String label) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(label + " doit être un nombre fini.");
        }
        return value;
    }

    private static double requirePositiveOrZero(double value, String label) {
        requireFinite(value, label);
        if (value < 0) {
            throw new IllegalArgumentException(label + " doit être positif ou nul.");
        }
        return value;
    }

    @Override
    public String toString() {
        return "FinancialStatement{" +
                "companyId=" + companyId +
                ", year=" + year +
                ", revenue=" + revenue +
                ", netIncome=" + netIncome +
                ", equity=" + equity +
                ", totalDebt=" + totalDebt +
                ", cash=" + cash +
                '}';
    }
}
