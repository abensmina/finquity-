# API Finquity

Serveur : `mvn spring-boot:run` puis http://localhost:8080
Le frontend (`src/Frontend`) est servi par le même serveur : http://localhost:8080/login.html

Toutes les réponses sont en JSON. En cas d'erreur : `{ "error": "message" }` avec le code 400 (données invalides), 401 (pas connecté) ou 500 (problème technique).
La connexion passe par un cookie de session : depuis le frontend, `fetch("/api/...")` l'envoie automatiquement.

## Comptes
| Méthode | Route | Corps (JSON) |
|---|---|---|
| POST | `/api/auth/register` | `companyName, siren, sector, username, email, password` |
| POST | `/api/auth/login` | `email, password` |
| POST | `/api/auth/logout` | |
| GET | `/api/auth/me` | |
| POST | `/api/auth/users` | `username, email, password` (ajoute un collègue) |
| POST | `/api/auth/password` | `oldPassword, newPassword` |

## Entreprise
| Méthode | Route | Corps (JSON) |
|---|---|---|
| GET | `/api/company` | |
| GET | `/api/company/users` | |
| GET | `/api/company/statements` | |
| POST | `/api/company/statements` | `year, revenue, netIncome, annualExpenses, equity, totalDebt, currentAssets, currentLiabilities, cash` |
| GET | `/api/company/diagnosis` | |

## Portefeuille
| Méthode | Route | Corps (JSON) |
|---|---|---|
| GET | `/api/portfolio` | |
| POST | `/api/portfolio/buy` | `symbol, quantity` |
| POST | `/api/portfolio/sell` | `symbol, quantity` |
| GET | `/api/portfolio/simulation?years=5&simulations=5000` | |

## Actifs et prix
| Méthode | Route | Corps (JSON) |
|---|---|---|
| GET | `/api/assets` | |
| POST | `/api/assets` | `symbol, name, type, currency, currentPrice` |
| GET | `/api/assets/{symbol}/prices` | |
| POST | `/api/assets/{symbol}/prices/update?days=750` | (nécessite TWELVE_DATA_API_KEY) |
| POST | `/api/assets/prices/update-all?days=750` | |

Exemple :
```js
const res = await fetch("/api/auth/login", {
  method: "POST",
  headers: { "Content-Type": "application/json" },
  body: JSON.stringify({ email: "lina@mail.com", password: "motdepasse1" }),
});
const data = await res.json();
if (!res.ok) alert(data.error);
```
