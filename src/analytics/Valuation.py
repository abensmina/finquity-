
import numpy as np
from scipy.stats import norm


def calculate_d1(S, K, T, r, sigma, q=0.0):
    if S <= 0 or K <= 0:
        raise ValueError("Le prix du sous-jacent et le strike doivent Ãªtre positifs.")

    if T <= 0 or sigma <= 0:
        raise ValueError("La maturitÃ© et la volatilitÃ© doivent Ãªtre positives.")

    if not all(np.isfinite(x) for x in (S, K, T, r, sigma, q)):
        raise ValueError("Les paramÃ¨tres doivent Ãªtre finis.")

    return (
        np.log(S / K) + (r - q + 0.5 * sigma**2) * T
    ) / (sigma * np.sqrt(T))


def black_scholes_call(S, K, T, r, sigma, q=0.0):
    d1 = calculate_d1(S, K, T, r, sigma, q)
    d2 = d1 - sigma * np.sqrt(T)

    call_price = (
        S * np.exp(-q * T) * norm.cdf(d1)
        - K * np.exp(-r * T) * norm.cdf(d2)
    )

    return call_price


def black_scholes_put(S, K, T, r, sigma, q=0.0):
    d1 = calculate_d1(S, K, T, r, sigma, q)
    d2 = d1 - sigma * np.sqrt(T)

    put_price = (
        K * np.exp(-r * T) * norm.cdf(-d2)
        - S * np.exp(-q * T) * norm.cdf(-d1)
    )

    return put_price


def calculate_option_delta(S, K, T, r, sigma, option_type="call", q=0.0):
    d1 = calculate_d1(S, K, T, r, sigma, q)

    if option_type == "call":
        return np.exp(-q * T) * norm.cdf(d1)

    if option_type == "put":
        return np.exp(-q * T) * (norm.cdf(d1) - 1)

    raise ValueError("Le type doit Ãªtre 'call' ou 'put'.")


def calculate_option_gamma(S, K, T, r, sigma, q=0.0):
    d1 = calculate_d1(S, K, T, r, sigma, q)

    return (
        np.exp(-q * T) * norm.pdf(d1)
        / (S * sigma * np.sqrt(T))
    )


def calculate_option_vega(S, K, T, r, sigma, q=0.0):
    d1 = calculate_d1(S, K, T, r, sigma, q)

    return (
        S * np.exp(-q * T) * norm.pdf(d1) * np.sqrt(T)
    )


def calculate_option_theta(S, K, T, r, sigma, option_type="call", q=0.0):
    d1 = calculate_d1(S, K, T, r, sigma, q)
    d2 = d1 - sigma * np.sqrt(T)

    first_term = (
        -S * np.exp(-q * T) * norm.pdf(d1) * sigma
        / (2 * np.sqrt(T))
    )

    if option_type == "call":
        return (
            first_term
            + q * S * np.exp(-q * T) * norm.cdf(d1)
            - r * K * np.exp(-r * T) * norm.cdf(d2)
        )

    if option_type == "put":
        return (
            first_term
            - q * S * np.exp(-q * T) * norm.cdf(-d1)
            + r * K * np.exp(-r * T) * norm.cdf(-d2)
        )

    raise ValueError("Le type doit Ãªtre 'call' ou 'put'.")


def monte_carlo_option_price(
    simulated_prices,
    K,
    r,
    T,
    option_type="call"
):
    simulated_prices = np.asarray(simulated_prices, dtype=np.float64)

    if simulated_prices.ndim != 1 or len(simulated_prices) < 1:
        raise ValueError("Les prix simulÃ©s doivent Ãªtre un vecteur non vide.")

    if not np.all(np.isfinite(simulated_prices)):
        raise ValueError("Les prix simulÃ©s doivent Ãªtre finis.")

    if K <= 0 or T <= 0:
        raise ValueError("Le strike et la maturitÃ© doivent Ãªtre positifs.")

    if not np.isfinite(r):
        raise ValueError("Le taux sans risque doit Ãªtre fini.")

    if option_type == "call":
        payoffs = np.maximum(simulated_prices - K, 0)

    elif option_type == "put":
        payoffs = np.maximum(K - simulated_prices, 0)

    else:
        raise ValueError("Le type doit Ãªtre 'call' ou 'put'.")

    option_price = np.exp(-r * T) * np.mean(payoffs)

    return option_price