
import numpy as np

def calculate_portfolio_returns(returns, weights):
    returns = np.asarray(returns, dtype=np.float64)
    weights = np.asarray(weights, dtype=np.float64)

    if returns.ndim != 2:
        raise ValueError("Les rendements doivent Ãªtre une matrice 2D.")

    if weights.ndim != 1 or len(weights) != returns.shape[0]:
        raise ValueError("Le nombre de poids doit correspondre au nombre d'actifs.")

    if not np.all(np.isfinite(returns)) or not np.all(np.isfinite(weights)):
        raise ValueError("Les donnÃ©es doivent Ãªtre finies.")

    if not np.isclose(np.sum(weights), 1.0):
        raise ValueError("La somme des poids doit Ãªtre Ã©gale Ã  1.")

    if returns.shape[1] < 1:
        raise ValueError("Il faut au moins une observation.")

    portfolio_returns = np.dot(weights, returns)

    return portfolio_returns

def calculate_volatility(returns, periods_per_year=252):
    returns = np.asarray(returns, dtype=np.float64)

    if returns.ndim not in (1, 2):
        raise ValueError("Les rendements doivent Ãªtre un vecteur ou une matrice 2D.")

    if returns.shape[-1] < 2:
        raise ValueError("Il faut au moins deux observations.")

    if not np.all(np.isfinite(returns)):
        raise ValueError("Les rendements doivent Ãªtre finis.")

    if periods_per_year <= 0:
        raise ValueError("Le nombre de pÃ©riodes par an doit Ãªtre positif.")

    volatility = np.std(returns, axis=-1, ddof=1)

    annual_volatility = volatility * np.sqrt(periods_per_year)

    return annual_volatility

def calculate_var(returns, confidence_level=0.95):
    returns = np.asarray(returns, dtype=np.float64)

    if returns.ndim != 1 or len(returns) == 0:
        raise ValueError("Les rendements doivent Ãªtre un vecteur non vide.")

    if not np.all(np.isfinite(returns)):
        raise ValueError("Les rendements doivent Ãªtre finis.")

    if not 0 < confidence_level < 1:
        raise ValueError("Le niveau de confiance doit Ãªtre entre 0 et 1.")

    losses = -returns

    var = np.quantile(losses, confidence_level)

    return var


def calculate_expected_shortfall(returns, confidence_level=0.95):
    returns = np.asarray(returns, dtype=np.float64)

    if returns.ndim != 1 or len(returns) == 0:
        raise ValueError("Les rendements doivent Ãªtre un vecteur non vide.")

    if not np.all(np.isfinite(returns)):
        raise ValueError("Les rendements doivent Ãªtre finis.")

    if not 0 < confidence_level < 1:
        raise ValueError("Le niveau de confiance doit Ãªtre entre 0 et 1.")

    losses = -returns

    var = calculate_var(returns, confidence_level)

    tail_size = max(1, int(np.ceil((1 - confidence_level) * len(losses))))

    worst_losses = np.sort(losses)[-tail_size:]

    expected_shortfall = np.mean(worst_losses)

    return expected_shortfall


def calculate_max_drawdown(returns, initial_value=1.0):
    returns = np.asarray(returns, dtype=np.float64)

    if returns.ndim != 1 or len(returns) == 0:
        raise ValueError("Les rendements doivent Ãªtre un vecteur non vide.")

    if not np.all(np.isfinite(returns)):
        raise ValueError("Les rendements doivent Ãªtre finis.")

    if np.any(returns < -1):
        raise ValueError("Un rendement ne peut pas Ãªtre infÃ©rieur Ã  -100%.")

    if initial_value <= 0:
        raise ValueError("La valeur initiale doit Ãªtre positive.")

    portfolio_values = initial_value * np.cumprod(1 + returns)

    portfolio_values = np.insert(portfolio_values, 0, initial_value)

    peaks = np.maximum.accumulate(portfolio_values)

    drawdowns = 1 - portfolio_values / peaks

    max_drawdown = np.max(drawdowns)

    return max_drawdown


def calculate_sharpe_ratio(returns, risk_free_rate=0.0, periods_per_year=252):
    returns = np.asarray(returns, dtype=np.float64)

    if returns.ndim != 1 or len(returns) < 2:
        raise ValueError("Les rendements doivent Ãªtre un vecteur avec au moins deux observations.")

    if not np.all(np.isfinite(returns)):
        raise ValueError("Les rendements doivent Ãªtre finis.")

    if periods_per_year <= 0:
        raise ValueError("Le nombre de pÃ©riodes par an doit Ãªtre positif.")

    if not np.isfinite(risk_free_rate) or risk_free_rate <= -1:
        raise ValueError("Le taux sans risque doit Ãªtre supÃ©rieur Ã  -100%.")

    volatility = np.std(returns, ddof=1)

    if volatility == 0:
        raise ValueError("Le Sharpe Ratio ne peut pas Ãªtre calculÃ© avec une volatilitÃ© nulle.")

    daily_risk_free_rate = (1 + risk_free_rate) ** (1 / periods_per_year) - 1

    excess_returns = returns - daily_risk_free_rate

    sharpe_ratio = (
        np.mean(excess_returns) / volatility
    ) * np.sqrt(periods_per_year)

    return sharpe_ratio


def analyze_portfolio(returns, weights, risk_free_rate=0.0,
                      confidence_level=0.95, periods_per_year=252):

    portfolio_returns = calculate_portfolio_returns(returns, weights)

    results = {
        "volatility": calculate_volatility(
            portfolio_returns, periods_per_year
        ),
        "var": calculate_var(
            portfolio_returns, confidence_level
        ),
        "expected_shortfall": calculate_expected_shortfall(
            portfolio_returns, confidence_level
        ),
        "max_drawdown": calculate_max_drawdown(
            portfolio_returns
        ),
        "sharpe_ratio": calculate_sharpe_ratio(
            portfolio_returns, risk_free_rate, periods_per_year
        )
    }

    return results