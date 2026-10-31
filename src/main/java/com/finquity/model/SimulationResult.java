package com.finquity.model;

/*
 * Résultat d'une simulation Monte-Carlo de la valeur future d'un portefeuille.
 * Pour chaque année : un scénario pessimiste (5 %), médian (50 %) et optimiste (95 %).
 */
public class SimulationResult {
    private final double initialValue;
    private final double[] pessimistic;  // 5 % des scénarios font moins bien
    private final double[] median;
    private final double[] optimistic;   // 5 % des scénarios font mieux
    private final double finalMean;
    private final double probabilityOfLoss;
    private final double expectedAnnualReturn;
    private final double annualVolatility;

    public SimulationResult(double initialValue, double[] pessimistic, double[] median,
                            double[] optimistic, double finalMean, double probabilityOfLoss,
                            double expectedAnnualReturn, double annualVolatility) {
        this.initialValue = initialValue;
        this.pessimistic = pessimistic.clone();
        this.median = median.clone();
        this.optimistic = optimistic.clone();
        this.finalMean = finalMean;
        this.probabilityOfLoss = probabilityOfLoss;
        this.expectedAnnualReturn = expectedAnnualReturn;
        this.annualVolatility = annualVolatility;
    }

    public double getInitialValue() {
        return initialValue;
    }

    public int getYears() {
        return median.length;
    }

    // Tableaux indexés par année : [0] = fin de la 1re année.
    public double[] getPessimistic() {
        return pessimistic.clone();
    }

    public double[] getMedian() {
        return median.clone();
    }

    public double[] getOptimistic() {
        return optimistic.clone();
    }

    public double getFinalMean() {
        return finalMean;
    }

    public double getProbabilityOfLoss() {
        return probabilityOfLoss;
    }

    public double getExpectedAnnualReturn() {
        return expectedAnnualReturn;
    }

    public double getAnnualVolatility() {
        return annualVolatility;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Valeur initiale : %,.0f €%n", initialValue));
        sb.append("Année   Pessimiste      Médian   Optimiste\n");
        for (int i = 0; i < median.length; i++) {
            sb.append(String.format("%5d %12.0f %11.0f %11.0f%n",
                    i + 1, pessimistic[i], median[i], optimistic[i]));
        }
        sb.append(String.format("Rendement annuel attendu : %.1f %%%n", expectedAnnualReturn * 100));
        sb.append(String.format("Volatilité annuelle      : %.1f %%%n", annualVolatility * 100));
        sb.append(String.format("Probabilité de perte     : %.1f %%", probabilityOfLoss * 100));
        return sb.toString();
    }
}
