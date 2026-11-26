import numpy as np
from scipy import stats
from sklearn.experimental import enable_iterative_imputer
from sklearn.impute import IterativeImputer
from sklearn.linear_model import BayesianRidge


def validate_prices(prices):

    prices = np.asarray(prices, dtype=np.float64)

    if prices.size == 0:
        raise ValueError("La matrice des prix ne peut pas Ãªtre vide")
    if prices.ndim != 2:
        raise ValueError("La matrice doit Ãªtre 2D")
    if prices.shape[1] < 2:
        raise ValueError("La matrice doit contenir minimum deux observations")
    if np.any(np.isinf(prices)):
        raise ValueError("La matrice contient des valeurs infinis")
    if np.any(prices <= 0):
        raise ValueError("La matrice doit contenir des prix strictement positifs")
    return prices


def calculate_returns(prices):
    prices = validate_prices(prices)  # VÃ©rification de la validitÃ© des prix

    rendement = (prices[:, 1:] / prices[:, :-1]) - 1  # Calcul des rendements simples

    return rendement


def count_missing_values(prices):

    # Nombre de valeurs manquantes pour chaque actif
    number_of_missing = np.sum(np.isnan(prices), axis=1)
    print("le nombre des valeurs manquantes est :", number_of_missing)

    # Proportion de valeurs manquantes par rapport au nombre d'observations
    rate_of_missing = number_of_missing / prices.shape[1]
    print("le pourcentage des valeurs manquantes est :", rate_of_missing * 100, "%")

    # Les actifs ayant plus de 20 % de valeurs manquantes sont Ã©cartÃ©s
    valid_assets = rate_of_missing <= 0.20
    clean_prices = prices[valid_assets, :]

    return clean_prices, valid_assets


def detect_outliers(rendement):

    # Calcul des Z-scores pour chaque actif, en ignorant les valeurs manquantes
    outliers = stats.zscore(rendement, axis=1, nan_policy="omit")

    # Une observation est considÃ©rÃ©e comme atypique si son Z-score dÃ©passe 3 en valeur absolue
    mask_outliers = np.abs(outliers) > 3

    # Calcul de la proportion de valeurs atypiques pour chaque actif
    number_of_outliers = np.sum(mask_outliers, axis=1)
    outlier_rate = number_of_outliers / outliers.shape[1]

    # Les actifs prÃ©sentant au maximum 5 % de valeurs atypiques sont conservÃ©s
    valid_assets_outlier = outlier_rate <= 0.05

    return mask_outliers, outlier_rate, valid_assets_outlier


def impute_missing_values(prices):

    prices = validate_prices(prices)

    # Aucune imputation n'est nÃ©cessaire si les donnÃ©es sont complÃ¨tes
    if not np.isnan(prices).any():
        return prices.copy()

    # VÃ©rification de la disponibilitÃ© des observations pour chaque actif
    if np.any(np.all(np.isnan(prices), axis=1)):
        raise ValueError("Un actif ne possÃ¨de aucune observation disponible")

    # VÃ©rification de la disponibilitÃ© des donnÃ©es pour chaque date
    if np.any(np.all(np.isnan(prices), axis=0)):
        raise ValueError("Une date ne possÃ¨de aucun prix disponible")

    # L'imputation multivariÃ©e nÃ©cessite plusieurs actifs
    if prices.shape[0] < 2:
        raise ValueError("Au moins deux actifs sont nÃ©cessaires")

    # Passage aux logarithmes pour travailler sur les log-prix
    log_prices = np.log(prices)

    # Initialisation de l'imputeur basÃ© sur une rÃ©gression bayÃ©sienne
    imputer = IterativeImputer(
        estimator=BayesianRidge(),
        initial_strategy="median",
        max_iter=20,
        tol=1e-3,
        skip_complete=True
    )

    # Transposition pour placer les dates en lignes et les actifs en colonnes
    imputed_log_prices = imputer.fit_transform(log_prices.T)

    # Retour Ã  l'Ã©chelle des prix et Ã  la disposition initiale
    imputed_prices = np.exp(imputed_log_prices.T)

    # Conservation des prix rÃ©ellement observÃ©s
    observed_mask = ~np.isnan(prices)
    imputed_prices[observed_mask] = prices[observed_mask]

    return imputed_prices


def clean_data(prices):

    # Validation initiale des donnÃ©es
    prices = validate_prices(prices)

    # Identification et suppression des actifs trop incomplets
    clean_prices, valid_assets_missing = count_missing_values(prices)

    # Estimation des prix manquants
    imputed_prices = impute_missing_values(clean_prices)

    # Calcul des rendements Ã  partir des prix complÃ©tÃ©s
    returns = calculate_returns(imputed_prices)

    # DÃ©tection des rendements atypiques
    mask_outliers, outlier_rate, valid_assets_outlier = detect_outliers(returns)

    # Regroupement des donnÃ©es nettoyÃ©es et des rÃ©sultats de diagnostic
    return {
        "prices": imputed_prices,
        "returns": returns,
        "valid_assets_missing": valid_assets_missing,
        "mask_outliers": mask_outliers,
        "outlier_rate": outlier_rate,
        "valid_assets_outlier": valid_assets_outlier
    }