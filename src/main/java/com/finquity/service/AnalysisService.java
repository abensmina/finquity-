package com.finquity.service;

import com.finquity.data.DataBase;
import com.finquity.model.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/*
 * Diagnostic financier d'une PME à partir de ses comptes annuels :
 * ratios -> score sur 100 -> profil de risque -> montant plaçable.
 *
 * Les seuils sont volontairement simples et lisibles : ils pourront
 * être affinés par secteur plus tard.
 */
public class AnalysisService {

    private final DataBase db;

    public AnalysisService(DataBase db) {
        this.db = Objects.requireNonNull(db, "La base de données est obligatoire.");
    }

    // Diagnostic sur le dernier exercice enregistré de l'entreprise.
    public CompanyDiagnosis diagnose(int companyId) {
        FinancialStatement latest = db.getLatestFinancialStatement(companyId).orElseThrow(
                () -> new IllegalArgumentException(
                        "Aucun compte annuel enregistré pour l'entreprise " + companyId));

        // L'exercice précédent sert à mesurer la croissance ; il peut manquer.
        FinancialStatement previous =
                db.findFinancialStatement(companyId, latest.getYear() - 1).orElse(null);

        return diagnose(latest, previous);
    }

    // Version sans base de données : utile pour les tests et les simulations.
    public CompanyDiagnosis diagnose(FinancialStatement fs, FinancialStatement previous) {
        Objects.requireNonNull(fs, "Les comptes ne peuvent pas être nuls.");
        List<String> alerts = new ArrayList<>();

        // ---------- 1. Ratios ----------

        double debtToEquity = fs.getEquity() > 0
                ? fs.getTotalDebt() / fs.getEquity()
                : Double.POSITIVE_INFINITY; // capitaux propres nuls ou négatifs

        double currentRatio = fs.getCurrentLiabilities() > 0
                ? fs.getCurrentAssets() / fs.getCurrentLiabilities()
                : Double.POSITIVE_INFINITY; // aucune dette à court terme

        double netMargin = fs.getRevenue() > 0
                ? fs.getNetIncome() / fs.getRevenue()
                : Double.NaN;

        double returnOnEquity = fs.getEquity() > 0
                ? fs.getNetIncome() / fs.getEquity()
                : Double.NaN;

        double revenueGrowth = (previous != null && previous.getRevenue() > 0)
                ? (fs.getRevenue() - previous.getRevenue()) / previous.getRevenue()
                : Double.NaN;

        // ---------- 2. Score sur 100 ----------
        // Solvabilité 30 + liquidité 25 + rentabilité 25 + croissance 20

        int score = 0;

        if (fs.getEquity() <= 0) {
            alerts.add("Capitaux propres nuls ou négatifs : situation de solvabilité critique.");
        } else if (debtToEquity < 0.5) {
            score += 30;
        } else if (debtToEquity < 1.0) {
            score += 20;
        } else if (debtToEquity < 2.0) {
            score += 10;
        } else {
            alerts.add("Endettement élevé (dettes supérieures à 2 fois les capitaux propres).");
        }

        if (currentRatio >= 1.5) {
            score += 25;
        } else if (currentRatio >= 1.2) {
            score += 18;
        } else if (currentRatio >= 1.0) {
            score += 10;
        } else {
            alerts.add("Liquidité insuffisante : les dettes à court terme dépassent l'actif circulant.");
        }

        if (fs.getNetIncome() < 0) {
            alerts.add("L'entreprise est en perte sur l'exercice.");
        } else {
            if (netMargin >= 0.10) {
                score += 15;
            } else if (netMargin >= 0.05) {
                score += 10;
            } else if (netMargin > 0) {
                score += 5;
            }

            if (returnOnEquity >= 0.15) {
                score += 10;
            } else if (returnOnEquity >= 0.08) {
                score += 6;
            } else if (returnOnEquity > 0) {
                score += 3;
            }
        }

        if (Double.isNaN(revenueGrowth)) {
            score += 10; // pas d'historique : on reste neutre
        } else if (revenueGrowth >= 0.05) {
            score += 20;
        } else if (revenueGrowth >= 0) {
            score += 12;
        } else {
            score += 4;
            alerts.add("Chiffre d'affaires en baisse sur un an.");
        }

        // ---------- 3. Profil de risque ----------

        RiskProfile profile;
        if (score >= 70) {
            profile = RiskProfile.DYNAMIQUE;
        } else if (score >= 45) {
            profile = RiskProfile.EQUILIBRE;
        } else {
            profile = RiskProfile.PRUDENT;
        }

        // Garde-fou : une entreprise fragile reste prudente, quel que soit son score.
        boolean fragile = fs.getNetIncome() < 0 || fs.getEquity() <= 0 || currentRatio < 1.0;
        if (fragile) {
            profile = RiskProfile.PRUDENT;
        }

        // ---------- 4. Montant plaçable ----------
        // On garde quelques mois de charges en trésorerie, selon le profil.

        double reserve = fs.getAnnualExpenses() / 12.0 * profile.getReserveMonths();
        double investableAmount = Math.max(0, fs.getCash() - reserve);

        if (investableAmount == 0) {
            alerts.add("Trésorerie insuffisante pour placer après la réserve de sécurité.");
        }

        return new CompanyDiagnosis(fs.getCompanyId(), fs.getYear(),
                debtToEquity, currentRatio, netMargin, returnOnEquity, revenueGrowth,
                score, profile, reserve, investableAmount, alerts);
    }
}
