
import numpy as np
import matplotlib.pyplot as plt

def estimate_gbm_parameters(returns, periods_per_year=252):

    returns = np.asarray(returns, dtype=np.float64)

    if returns.ndim != 2:
        raise ValueError("Les rendements doivent Ãªtre une matrice 2D")
    if returns.shape[0] < 1 or returns.shape[1] < 2:
        raise ValueError("Il faut au moins un actif et deux observations")
    if not np.all(np.isfinite(returns)):
        raise ValueError("Les rendements contiennent des valeurs invalides")
    if np.any(returns <= -1):
        raise ValueError("Les rendements doivent Ãªtre supÃ©rieurs Ã  -100 %")
    if not np.isfinite(periods_per_year) or periods_per_year <= 0:
        raise ValueError("periods_per_year doit Ãªtre positif")

    # Calcul des log-rendements
    log_returns = np.log1p(returns)

    # Estimation du drift et de la volatilitÃ©
    sigma = np.std(log_returns, axis=1, ddof=1) * np.sqrt(periods_per_year)

    mu = (
        np.mean(log_returns, axis=1) * periods_per_year
        + 0.5 * sigma**2
    )

    # Matrice de corrÃ©lation
    n_assets = returns.shape[0]
    correlation = np.eye(n_assets)

    active_assets = sigma > 0

    if np.sum(active_assets) > 1:
        active_returns = log_returns[active_assets, :]
        active_corr = np.corrcoef(active_returns)
        correlation[np.ix_(active_assets, active_assets)] = active_corr

    return mu, sigma, correlation


def correlation_factor(correlation):

    correlation = np.asarray(correlation, dtype=np.float64)

    if correlation.ndim != 2:
        raise ValueError("La corrÃ©lation doit Ãªtre une matrice 2D")
    if correlation.shape[0] != correlation.shape[1]:
        raise ValueError("La matrice de corrÃ©lation doit Ãªtre carrÃ©e")
    if not np.all(np.isfinite(correlation)):
        raise ValueError("La matrice contient des valeurs invalides")
    if not np.allclose(correlation, correlation.T, atol=1e-8):
        raise ValueError("La matrice doit Ãªtre symÃ©trique")
    if not np.allclose(np.diag(correlation), 1, atol=1e-8):
        raise ValueError("La diagonale doit Ãªtre Ã©gale Ã  1")
    if np.any(np.abs(correlation) > 1 + 1e-8):
        raise ValueError("Les corrÃ©lations doivent Ãªtre comprises entre -1 et 1")

    eigenvalues, eigenvectors = np.linalg.eigh(correlation)

    if np.min(eigenvalues) < -1e-8:
        raise ValueError("La matrice n'est pas semi-dÃ©finie positive")

    eigenvalues = np.maximum(eigenvalues, 0)

    factor = eigenvectors @ np.diag(np.sqrt(eigenvalues))

    return factor


def simulate_gbm(
    initial_prices,
    mu,
    sigma,
    correlation,
    n_steps=252,
    n_simulations=10000,
    periods_per_year=252,
    seed=None
):

    initial_prices = np.asarray(initial_prices, dtype=np.float64)
    mu = np.asarray(mu, dtype=np.float64)
    sigma = np.asarray(sigma, dtype=np.float64)

    if initial_prices.ndim != 1:
        raise ValueError("Les prix initiaux doivent Ãªtre un vecteur")
    if mu.shape != initial_prices.shape or sigma.shape != initial_prices.shape:
        raise ValueError("Les paramÃ¨tres doivent correspondre aux actifs")
    if not np.all(np.isfinite(initial_prices)) or np.any(initial_prices <= 0):
        raise ValueError("Les prix initiaux doivent Ãªtre positifs et finis")
    if not np.all(np.isfinite(mu)) or not np.all(np.isfinite(sigma)):
        raise ValueError("Les paramÃ¨tres doivent Ãªtre finis")
    if np.any(sigma < 0):
        raise ValueError("La volatilitÃ© ne peut pas Ãªtre nÃ©gative")
    if not isinstance(n_steps, (int, np.integer)) or isinstance(n_steps, (bool, np.bool_)) or n_steps <= 0:
        raise ValueError("n_steps doit Ãªtre un entier positif")
    if not isinstance(n_simulations, (int, np.integer)) or isinstance(n_simulations, (bool, np.bool_)) or n_simulations <= 0:
        raise ValueError("n_simulations doit Ãªtre un entier positif")
    if not np.isfinite(periods_per_year) or periods_per_year <= 0:
        raise ValueError("periods_per_year doit Ãªtre positif")

    correlation = np.asarray(correlation, dtype=np.float64)

    if correlation.shape != (len(initial_prices), len(initial_prices)):
        raise ValueError("La matrice de corrÃ©lation ne correspond pas aux actifs")

    rng = np.random.default_rng(seed)

    L = correlation_factor(correlation)

    dt = 1 / periods_per_year

    # GÃ©nÃ©ration des variables alÃ©atoires
    Z = rng.standard_normal(
        size=(len(initial_prices), n_simulations, n_steps)
    )

    # Introduction des corrÃ©lations entre les actifs
    correlated_Z = (
        L @ Z.reshape(len(initial_prices), -1)
    ).reshape(len(initial_prices), n_simulations, n_steps)

    # Calcul des variations logarithmiques
    increments = (
        (mu[:, None, None] - 0.5 * sigma[:, None, None]**2) * dt
        + sigma[:, None, None] * np.sqrt(dt) * correlated_Z
    )

    log_paths = np.concatenate(
        [
            np.zeros((len(initial_prices), n_simulations, 1)),
            np.cumsum(increments, axis=2)
        ],
        axis=2
    )

    simulated_prices = initial_prices[:, None, None] * np.exp(log_paths)

    return simulated_prices


def plot_simulations(simulated_prices, asset_index=0, n_paths=100):
    simulated_prices = np.asarray(simulated_prices, dtype=np.float64)
    if simulated_prices.ndim != 3:
        raise ValueError("Les simulations doivent Ãªtre une matrice 3D")
    if asset_index < 0 or asset_index >= simulated_prices.shape[0]:
        raise ValueError("Indice d'actif invalide")
    if n_paths <= 0:
        raise ValueError("Le nombre de trajectoires doit Ãªtre positif")
    n_paths = min(n_paths, simulated_prices.shape[1])
    plt.figure(figsize=(12, 6))
    for i in range(n_paths):
        plt.plot(simulated_prices[asset_index, i, :], alpha=0.5)
    plt.title(f"Monte Carlo Simulation{asset_index}")
    plt.xlabel("Time steps")
    plt.ylabel("Price")
    plt.grid(True, alpha=0.3)
    plt.tight_layout()
    plt.show()
