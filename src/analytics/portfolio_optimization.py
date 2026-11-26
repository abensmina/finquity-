
import numpy as np
from scipy.optimize import minimize


def calculate_covariance_matrix(returns, periods_per_year=252):
    returns = np.asarray(returns, dtype=np.float64)

    if returns.ndim != 2:
        raise ValueError("Les rendements doivent Ãªtre une matrice 2D.")

    if returns.shape[0] < 1 or returns.shape[1] < 2:
        raise ValueError("Il faut au moins un actif et deux observations.")

    if not np.all(np.isfinite(returns)):
        raise ValueError("Les rendements doivent Ãªtre finis.")

    if periods_per_year <= 0:
        raise ValueError("Le nombre de pÃ©riodes par an doit Ãªtre positif.")

    covariance_matrix = np.cov(returns, ddof=1)

    annual_covariance_matrix = covariance_matrix * periods_per_year

    return annual_covariance_matrix


def calculate_portfolio_performance(weights, mean_returns, covariance_matrix):
    weights = np.asarray(weights, dtype=np.float64)
    mean_returns = np.asarray(mean_returns, dtype=np.float64)
    covariance_matrix = np.asarray(covariance_matrix, dtype=np.float64)

    n_assets = len(mean_returns)

    if weights.ndim != 1 or len(weights) != n_assets:
        raise ValueError("Le nombre de poids doit correspondre au nombre d'actifs.")

    if covariance_matrix.shape != (n_assets, n_assets):
        raise ValueError("La matrice de covariance a une dimension incorrecte.")

    if not np.all(np.isfinite(weights)):
        raise ValueError("Les poids doivent Ãªtre finis.")

    if not np.all(np.isfinite(mean_returns)):
        raise ValueError("Les rendements moyens doivent Ãªtre finis.")

    if not np.all(np.isfinite(covariance_matrix)):
        raise ValueError("La matrice de covariance doit Ãªtre finie.")

    if not np.isclose(np.sum(weights), 1.0):
        raise ValueError("La somme des poids doit Ãªtre Ã©gale Ã  1.")

    portfolio_return = np.dot(weights, mean_returns)

    portfolio_variance = weights.T @ covariance_matrix @ weights

    portfolio_volatility = np.sqrt(max(portfolio_variance, 0.0))

    return portfolio_return, portfolio_volatility


def minimize_volatility(mean_returns, covariance_matrix, target_return=None):
    mean_returns = np.asarray(mean_returns, dtype=np.float64)
    covariance_matrix = np.asarray(covariance_matrix, dtype=np.float64)

    n_assets = len(mean_returns)

    if mean_returns.ndim != 1 or n_assets < 1:
        raise ValueError("Les rendements moyens doivent Ãªtre un vecteur non vide.")

    if covariance_matrix.shape != (n_assets, n_assets):
        raise ValueError("La matrice de covariance a une dimension incorrecte.")

    if not np.all(np.isfinite(mean_returns)) or not np.all(np.isfinite(covariance_matrix)):
        raise ValueError("Les paramÃ¨tres doivent Ãªtre finis.")

    if not np.allclose(covariance_matrix, covariance_matrix.T):
        raise ValueError("La matrice de covariance doit Ãªtre symÃ©trique.")

    if np.any(np.linalg.eigvalsh(covariance_matrix) < -1e-8):
        raise ValueError("La matrice de covariance doit Ãªtre semi-dÃ©finie positive.")

    if target_return is not None:
        if not np.isfinite(target_return):
            raise ValueError("Le rendement cible doit Ãªtre fini.")

        if target_return > np.max(mean_returns):
            raise ValueError("Le rendement cible est supÃ©rieur au rendement maximal accessible.")

        if target_return < np.min(mean_returns):
            raise ValueError("Le rendement cible est infÃ©rieur au rendement minimal accessible.")

    # Fonction objectif : variance du portefeuille
    def objective(weights):
        return weights.T @ covariance_matrix @ weights

    # Somme des poids Ã©gale Ã  1
    constraints = [
        {"type": "eq", "fun": lambda weights: np.sum(weights) - 1}
    ]

    # Rendement minimal imposÃ©
    if target_return is not None:
        constraints.append({
            "type": "ineq",
            "fun": lambda weights: np.dot(weights, mean_returns) - target_return
        })

    bounds = [(0, 1) for _ in range(n_assets)]

    initial_weights = np.ones(n_assets) / n_assets

    result = minimize(
        objective,
        initial_weights,
        method="SLSQP",
        bounds=bounds,
        constraints=constraints
    )

    if not result.success:
        raise RuntimeError(f"Ã‰chec de l'optimisation : {result.message}")

    return result.x


def maximize_sharpe_ratio(mean_returns, covariance_matrix, risk_free_rate=0.02):
    mean_returns = np.asarray(mean_returns, dtype=np.float64)
    covariance_matrix = np.asarray(covariance_matrix, dtype=np.float64)

    n_assets = len(mean_returns)

    if mean_returns.ndim != 1 or n_assets < 1:
        raise ValueError("Les rendements moyens doivent Ãªtre un vecteur non vide.")

    if covariance_matrix.shape != (n_assets, n_assets):
        raise ValueError("La matrice de covariance a une dimension incorrecte.")

    if not np.all(np.isfinite(mean_returns)) or not np.all(np.isfinite(covariance_matrix)):
        raise ValueError("Les paramÃ¨tres doivent Ãªtre finis.")

    if not np.allclose(covariance_matrix, covariance_matrix.T):
        raise ValueError("La matrice de covariance doit Ãªtre symÃ©trique.")

    if np.any(np.linalg.eigvalsh(covariance_matrix) < -1e-8):
        raise ValueError("La matrice de covariance doit Ãªtre semi-dÃ©finie positive.")

    if not np.isfinite(risk_free_rate):
        raise ValueError("Le taux sans risque doit Ãªtre fini.")

    # Fonction objectif : opposÃ© du Sharpe Ratio
    def objective(weights):
        portfolio_return = np.dot(weights, mean_returns)
        portfolio_variance = weights.T @ covariance_matrix @ weights

        if portfolio_variance <= 0:
            return 1e10

        portfolio_volatility = np.sqrt(portfolio_variance)

        sharpe_ratio = (portfolio_return - risk_free_rate) / portfolio_volatility

        return -sharpe_ratio

    constraints = [
        {"type": "eq", "fun": lambda weights: np.sum(weights) - 1}
    ]

    bounds = [(0, 1) for _ in range(n_assets)]

    initial_weights = np.ones(n_assets) / n_assets

    result = minimize(
        objective,
        initial_weights,
        method="SLSQP",
        bounds=bounds,
        constraints=constraints
    )

    if not result.success:
        raise RuntimeError(f"Ã‰chec de l'optimisation : {result.message}")

    return result.x


def efficient_frontier(mean_returns, covariance_matrix, n_portfolios=50):
    mean_returns = np.asarray(mean_returns, dtype=np.float64)
    covariance_matrix = np.asarray(covariance_matrix, dtype=np.float64)

    if n_portfolios < 2:
        raise ValueError("Il faut au moins deux portefeuilles.")

    min_return = np.min(mean_returns)
    max_return = np.max(mean_returns)

    target_returns = np.linspace(min_return, max_return, n_portfolios)

    frontier_returns = []
    frontier_volatilities = []
    frontier_weights = []

    for target in target_returns:
        weights = minimize_volatility(
            mean_returns,
            covariance_matrix,
            target_return=target
        )

        portfolio_return, portfolio_volatility = calculate_portfolio_performance(
            weights,
            mean_returns,
            covariance_matrix
        )

        frontier_returns.append(portfolio_return)
        frontier_volatilities.append(portfolio_volatility)
        frontier_weights.append(weights)

    return {
        "returns": np.array(frontier_returns),
        "volatilities": np.array(frontier_volatilities),
        "weights": np.array(frontier_weights)
    }