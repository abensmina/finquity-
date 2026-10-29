
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public class portfolio {

    /*
     * Map associant chaque actif à sa quantité détenue.
     * Exemple :
     * Apple (AAPL) -> 10 actions
     * Microsoft (MSFT) -> 5 actions
     *
     * LinkedHashMap conserve l'ordre d'insertion.
     * final empêche de remplacer la map, mais permet
     * toujours d'ajouter ou de retirer des actifs.
     */
    private final Map<Asset, Integer> assets;

    // Constructeur : crée un portefeuille vide.
    public portfolio() {
        this.assets = new LinkedHashMap<>();
    }

    // Ajoute un actif ou augmente la quantité déjà détenue.
    public void addAsset(Asset asset, int quantity) {

        // Vérifie que l'actif n'est pas null.
        Objects.requireNonNull(
            asset, "L'actif ne peut pas être nul."
        );

        // Vérifie que la quantité est strictement positive.
        if (quantity <= 0) {
            throw new IllegalArgumentException(
                "La quantité doit être strictement positive."
            );
        }

        /*
         * merge() ajoute la quantité à l'entrée existante
         * ou crée une nouvelle entrée si l'actif est absent.
         *
         * Math.addExact() détecte un dépassement de capacité
         * lors de l'addition des deux quantités.
         */
        assets.merge(asset, quantity, Math::addExact);
    }

    // Retire une quantité donnée d'un actif du portefeuille.
    public void removeAsset(Asset asset, int quantity) {

        Objects.requireNonNull(
            asset, "L'actif ne peut pas être nul."
        );

        if (quantity <= 0) {
            throw new IllegalArgumentException(
                "La quantité à retirer doit être strictement positive."
            );
        }

        // Récupère la quantité actuellement détenue.
        Integer currentQuantity = assets.get(asset);

        // Vérifie que l'actif est présent dans le portefeuille.
        if (currentQuantity == null) {
            throw new IllegalArgumentException(
                "L'actif n'existe pas dans le portefeuille."
            );
        }

        // Empêche de retirer plus que la quantité détenue.
        if (currentQuantity < quantity) {
            throw new IllegalArgumentException(
                "La quantité à retirer est supérieure à la quantité détenue."
            );
        }

        // Si toute la position est retirée, supprime l'entrée.
        if (currentQuantity == quantity) {
            assets.remove(asset);
        } else {
            // Sinon, met à jour la quantité restante.
            assets.put(asset, currentQuantity - quantity);
        }
    }

    /*
     * Retourne une vue non modifiable de la map.
     * Le code extérieur peut consulter les positions,
     * mais ne peut pas modifier directement la map.
     */
    public Map<Asset, Integer> getAssets() {
        return Collections.unmodifiableMap(assets);
    }

    /*
     * Calcule la valeur totale du portefeuille :
     * somme du prix courant multiplié par la quantité
     * détenue pour chaque actif.
     *
     * Tous les prix doivent être exprimés dans une
     * même devise pour que le total soit significatif.
     */
    public double getTotalValue() {
        double totalValue = 0.0;

        for (Map.Entry<Asset, Integer> entry : assets.entrySet()) {
            Asset asset = entry.getKey();
            int quantity = entry.getValue();

            totalValue += asset.getCurrentPrice() * quantity;
        }

        return totalValue;
    }

    // Retourne le nombre de positions différentes détenues.
    public int getNumberOfAssets() {
        return assets.size();
    }

    // Vérifie si cet actif est présent dans le portefeuille.
    public boolean containsAsset(Asset asset) {
        Objects.requireNonNull(
            asset, "L'actif ne peut pas être nul."
        );

        return assets.containsKey(asset);
    }
}
