package com.finquity.api;

import com.finquity.model.Asset;
import com.finquity.model.Company;
import com.finquity.model.portfolio;
import com.finquity.model.user;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/*
 * Les formes JSON échangées avec le frontend.
 * Un "record" est une petite classe de données : Spring le convertit en JSON
 * (et inversement) automatiquement.
 *
 * On ne renvoie jamais directement un `user` : il contient le hash du mot de passe.
 */
public final class Dto {

    private Dto() {
    }

    // ---------- Requêtes (ce que le frontend envoie) ----------

    public record RegisterRequest(String companyName, String siren, String sector,
                                  String username, String email, String password) {
    }

    public record AddUserRequest(String username, String email, String password) {
    }

    public record LoginRequest(String email, String password) {
    }

    public record ChangePasswordRequest(String oldPassword, String newPassword) {
    }

    public record StatementRequest(int year, double revenue, double netIncome, double annualExpenses,
                                   double equity, double totalDebt, double currentAssets,
                                   double currentLiabilities, double cash) {
    }

    public record TradeRequest(String symbol, int quantity) {
    }

    public record AssetRequest(String symbol, String name, String type, String currency,
                               double currentPrice) {
    }

    // ---------- Réponses (ce que le backend renvoie) ----------

    public record UserView(int id, int companyId, String username, String email) {
        public static UserView of(user u) {
            return new UserView(u.getId(), u.getCompanyId(), u.getUsername(), u.getEmail());
        }
    }

    public record CompanyView(int id, String name, String siren, String sector) {
        public static CompanyView of(Company c) {
            return new CompanyView(c.getId(), c.getName(), c.getSiren(), c.getSector());
        }
    }

    public record PositionView(String symbol, String name, String type, String currency,
                               double price, int quantity, double value, double weight) {
    }

    public record PortfolioView(double totalValue, List<PositionView> positions) {
        public static PortfolioView of(portfolio p) {
            double total = p.getTotalValue();
            List<PositionView> positions = new ArrayList<>();
            for (Map.Entry<Asset, Integer> e : p.getAssets().entrySet()) {
                Asset a = e.getKey();
                double value = a.getCurrentPrice() * e.getValue();
                positions.add(new PositionView(a.getSymbol(), a.getName(), a.getType(), a.getCurrency(),
                        a.getCurrentPrice(), e.getValue(), value, total > 0 ? value / total : 0));
            }
            return new PortfolioView(total, positions);
        }
    }
}
