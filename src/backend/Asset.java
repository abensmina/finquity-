
import java.util.Objects;

public class Asset {
    private final String name;
    private final String symbol;
    private final String type;
    private final String currency;
    private double currentPrice;

    public Asset(String symbol, String name, String type,
                 String currency, double currentPrice) {

        if (symbol == null || symbol.isBlank()) {
            throw new IllegalArgumentException(
                "Le symbole est obligatoire."
            );
        }

        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException(
                "Le nom de l'actif est obligatoire."
            );
        }

        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException(
                "Le type d'actif est obligatoire."
            );
        }

        if (currentPrice < 0 || !Double.isFinite(currentPrice)) {
            throw new IllegalArgumentException(
                "Le prix doit être positif ou nul et fini."
            );
        }

        // On normalise le symbole pour éviter les différences de casse.
        this.symbol = symbol.trim().toUpperCase();
        this.name = name;
        this.type = type;
        this.currency = currency;
        this.currentPrice = currentPrice;
    }

    public String getSymbol() {
        return symbol;
    }

    public String getName() {
        return name;
    }

    public String getType() {
        return type;
    }

    public String getCurrency() {
        return currency;
    }

    public double getCurrentPrice() {
        return currentPrice;
    }

    public void setCurrentPrice(double currentPrice) {
        if (currentPrice < 0 || !Double.isFinite(currentPrice)) {
            throw new IllegalArgumentException(
                "Le prix doit être positif ou nul et fini."
            );
        }

        this.currentPrice = currentPrice;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }

        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }

        Asset other = (Asset) obj;
        return symbol.equals(other.symbol);
    }

    @Override
    public int hashCode() {
        return Objects.hash(symbol);
    }

    @Override
    public String toString() {
        return "Asset{" +
                "symbol='" + symbol + '\'' +
                ", name='" + name + '\'' +
                ", type='" + type + '\'' +
                ", currency='" + currency + '\'' +
                ", currentPrice=" + currentPrice +
                '}';
    }
}
