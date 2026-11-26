
import numpy as np
from scipy.stats import skew, kurtosis

def calculate_mean_returns(returns, periods_per_year=252):
    returns = np.asarray(returns, dtype=np.float64)

    if returns.ndim != 2 or returns.shape[1] < 1:
        raise ValueError("Les rendements doivent Ãªtre une matrice 2D non vide.")

    if not np.all(np.isfinite(returns)):
        raise ValueError("Les rendements doivent Ãªtre finis.")

    if periods_per_year <= 0:
        raise ValueError("Le nombre de pÃ©riodes par an doit Ãªtre positif.")

    return np.mean(returns, axis=1) * periods_per_year


def calculate_variance(returns, periods_per_year=252):
    returns = np.asarray(returns, dtype=np.float64)

    if returns.ndim != 2 or returns.shape[1] < 2:
        raise ValueError("Il faut au moins deux observations.")

    if not np.all(np.isfinite(returns)):
        raise ValueError("Les rendements doivent Ãªtre finis.")

    if periods_per_year <= 0:
        raise ValueError("Le nombre de pÃ©riodes par an doit Ãªtre positif.")

    return np.var(returns, axis=1, ddof=1) * periods_per_year


def calculate_skewness(returns):
    returns = np.asarray(returns, dtype=np.float64)

    if returns.ndim != 2 or returns.shape[1] < 3:
        raise ValueError("Il faut au moins trois observations.")

    if not np.all(np.isfinite(returns)):
        raise ValueError("Les rendements doivent Ãªtre finis.")

    return skew(returns, axis=1, bias=False)


def calculate_kurtosis(returns):
    returns = np.asarray(returns, dtype=np.float64)

    if returns.ndim != 2 or returns.shape[1] < 4:
        raise ValueError("Il faut au moins quatre observations.")

    if not np.all(np.isfinite(returns)):
        raise ValueError("Les rendements doivent Ãªtre finis.")

    return kurtosis(returns, axis=1, fisher=True, bias=False)


def calculate_correlation_matrix(returns):
    returns = np.asarray(returns, dtype=np.float64)

    if returns.ndim != 2 or returns.shape[1] < 2:
        raise ValueError("Il faut au moins deux observations.")

    if not np.all(np.isfinite(returns)):
        raise ValueError("Les rendements doivent Ãªtre finis.")

    correlation_matrix = np.corrcoef(returns)

    if not np.all(np.isfinite(correlation_matrix)):
        raise ValueError("La corrÃ©lation est indÃ©finie pour au moins un actif.")

    return correlation_matrix


def calculate_rolling_volatility(returns, window=21, periods_per_year=252):
    returns = np.asarray(returns, dtype=np.float64)

    if returns.ndim != 2:
        raise ValueError("Les rendements doivent Ãªtre une matrice 2D.")

    if window < 2 or window > returns.shape[1]:
        raise ValueError("La fenÃªtre doit Ãªtre comprise entre 2 et le nombre d'observations.")

    if not np.all(np.isfinite(returns)):
        raise ValueError("Les rendements doivent Ãªtre finis.")

    if periods_per_year <= 0:
        raise ValueError("Le nombre de pÃ©riodes par an doit Ãªtre positif.")

    n_assets, n_observations = returns.shape

    rolling_volatility = np.empty(
        (n_assets, n_observations - window + 1)
    )

    for i in range(n_observations - window + 1):
        sample = returns[:, i:i + window]

        rolling_volatility[:, i] = (
            np.std(sample, axis=1, ddof=1) * np.sqrt(periods_per_year)
        )

    return rolling_volatility


def analyze_statistics(returns, periods_per_year=252):
    return {
        "mean_returns": calculate_mean_returns(
            returns, periods_per_year
        ),
        "variance": calculate_variance(
            returns, periods_per_year
        ),
        "volatility": np.sqrt(
            calculate_variance(returns, periods_per_year)
        ),
        "skewness": calculate_skewness(returns),
        "kurtosis": calculate_kurtosis(returns),
        "correlation_matrix": calculate_correlation_matrix(returns)
    }