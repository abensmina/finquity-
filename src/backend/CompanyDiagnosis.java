import java.util.Collections;
import java.util.List;

/*
 * Résultat du diagnostic financier d'une PME pour un exercice.
 * Produit par AnalysisService ; immuable.
 *
 * Un ratio vaut NaN quand il n'a pas de sens
 * (par exemple une rentabilité avec des capitaux propres négatifs).
 */
public class CompanyDiagnosis {
    private final int companyId;
    private final int year;

    private final double debtToEquity;   // solvabilité : dettes / capitaux propres
    private final double currentRatio;   // liquidité : actif circulant / passif circulant
    private final double netMargin;      // rentabilité : résultat net / chiffre d'affaires
    private final double returnOnEquity; // rentabilité : résultat net / capitaux propres
    private final double revenueGrowth;  // évolution du CA sur un an (NaN si pas d'historique)

    private final int score;             // note de santé financière, de 0 à 100
    private final RiskProfile profile;
    private final double reserve;          // trésorerie à garder de côté
    private final double investableAmount; // trésorerie que l'on peut placer
    private final List<String> alerts;

    public CompanyDiagnosis(int companyId, int year,
                            double debtToEquity, double currentRatio,
                            double netMargin, double returnOnEquity, double revenueGrowth,
                            int score, RiskProfile profile,
                            double reserve, double investableAmount,
                            List<String> alerts) {
        this.companyId = companyId;
        this.year = year;
        this.debtToEquity = debtToEquity;
        this.currentRatio = currentRatio;
        this.netMargin = netMargin;
        this.returnOnEquity = returnOnEquity;
        this.revenueGrowth = revenueGrowth;
        this.score = score;
        this.profile = profile;
        this.reserve = reserve;
        this.investableAmount = investableAmount;
        this.alerts = Collections.unmodifiableList(List.copyOf(alerts));
    }

    public int getCompanyId() {
        return companyId;
    }

    public int getYear() {
        return year;
    }

    public double getDebtToEquity() {
        return debtToEquity;
    }

    public double getCurrentRatio() {
        return currentRatio;
    }

    public double getNetMargin() {
        return netMargin;
    }

    public double getReturnOnEquity() {
        return returnOnEquity;
    }

    public double getRevenueGrowth() {
        return revenueGrowth;
    }

    public int getScore() {
        return score;
    }

    public RiskProfile getProfile() {
        return profile;
    }

    public double getReserve() {
        return reserve;
    }

    public double getInvestableAmount() {
        return investableAmount;
    }

    public List<String> getAlerts() {
        return alerts;
    }

    // Résumé lisible, pratique pour les tests et plus tard pour les rapports.
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Diagnostic entreprise ").append(companyId).append(" (").append(year).append(")\n");
        sb.append("  Dettes / capitaux propres : ").append(ratio(debtToEquity)).append('\n');
        sb.append("  Liquidité générale        : ").append(ratio(currentRatio)).append('\n');
        sb.append("  Marge nette               : ").append(percent(netMargin)).append('\n');
        sb.append("  Rentabilité des capitaux  : ").append(percent(returnOnEquity)).append('\n');
        sb.append("  Croissance du CA          : ").append(percent(revenueGrowth)).append('\n');
        sb.append("  Score                     : ").append(score).append(" / 100\n");
        sb.append("  Profil                    : ").append(profile.getLabel()).append('\n');
        sb.append("  Réserve conservée         : ").append(money(reserve)).append('\n');
        sb.append("  Montant plaçable          : ").append(money(investableAmount));
        for (String alert : alerts) {
            sb.append("\n  ⚠ ").append(alert);
        }
        return sb.toString();
    }

    private static String ratio(double value) {
        if (Double.isNaN(value)) {
            return "n/a";
        }
        if (Double.isInfinite(value)) {
            return "∞";
        }
        return String.format("%.2f", value);
    }

    private static String percent(double value) {
        return Double.isNaN(value) ? "n/a" : String.format("%.1f %%", value * 100);
    }

    private static String money(double value) {
        return String.format("%,.0f €", value);
    }
}
