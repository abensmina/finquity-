/*
 * Profil d'investissement d'une PME, déduit de sa santé financière.
 * Chaque profil fixe des limites que l'allocation devra respecter.
 * ie argent placé en actions, obligations, monétaire, donc l'argent alloue aux placements financiers.
 */
public enum RiskProfile {

    //          libellé      mois de réserve  part max en actions  part min en placements sûrs
    PRUDENT   ("Prudent",    6,               0.10,                0.70),
    EQUILIBRE ("Équilibré",  4,               0.30,                0.40),
    DYNAMIQUE ("Dynamique",  3,               0.50,                0.20);

    private final String label;
    private final int reserveMonths;      // mois de charges gardés en trésorerie
    private final double maxEquityShare;  // part maximale du placement en actions
    private final double minSafeShare;    // part minimale en monétaire / comptes à terme

    RiskProfile(String label, int reserveMonths, double maxEquityShare, double minSafeShare) {
        this.label = label;
        this.reserveMonths = reserveMonths;
        this.maxEquityShare = maxEquityShare;
        this.minSafeShare = minSafeShare;
    }

    public String getLabel() {
        return label;
    }

    public int getReserveMonths() {
        return reserveMonths;
    }

    public double getMaxEquityShare() {
        return maxEquityShare;
    }

    public double getMinSafeShare() {
        return minSafeShare;
    }
}
