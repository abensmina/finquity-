import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

public class DataBase implements AutoCloseable {

    private final Connection connection;

    // Ouvre (ou crée) le fichier SQLite, par exemple "finquity.db".
    public DataBase(String filePath) {
        try {
            connection = DriverManager.getConnection("jdbc:sqlite:" + filePath);
            try (Statement st = connection.createStatement()) {
                // SQLite n'applique les clés étrangères que si on le demande.
                st.execute("PRAGMA foreign_keys = ON");
            }
            createTables();
        } catch (SQLException e) {
            throw new IllegalStateException("Impossible d'ouvrir la base : " + filePath, e);
        }
    }

    private void createTables() throws SQLException {
        try (Statement st = connection.createStatement()) {
            st.execute("CREATE TABLE IF NOT EXISTS companies ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + "name TEXT NOT NULL, "
                    + "siren TEXT NOT NULL UNIQUE, "
                    + "sector TEXT)");

            // Supprimer une entreprise supprime ses utilisateurs (CASCADE).
            st.execute("CREATE TABLE IF NOT EXISTS users ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + "company_id INTEGER NOT NULL REFERENCES companies(id) ON DELETE CASCADE, "
                    + "username TEXT NOT NULL, "
                    + "email TEXT NOT NULL UNIQUE, "
                    + "password_hash TEXT NOT NULL)");

            // Une ligne = les comptes d'une entreprise pour une année.
            st.execute("CREATE TABLE IF NOT EXISTS financial_statements ("
                    + "company_id INTEGER NOT NULL REFERENCES companies(id) ON DELETE CASCADE, "
                    + "year INTEGER NOT NULL, "
                    + "revenue REAL NOT NULL, "
                    + "net_income REAL NOT NULL, "
                    + "annual_expenses REAL NOT NULL, "
                    + "equity REAL NOT NULL, "
                    + "total_debt REAL NOT NULL, "
                    + "current_assets REAL NOT NULL, "
                    + "current_liabilities REAL NOT NULL, "
                    + "cash REAL NOT NULL, "
                    + "PRIMARY KEY (company_id, year))");

            st.execute("CREATE TABLE IF NOT EXISTS assets ("
                    + "symbol TEXT PRIMARY KEY, "
                    + "name TEXT NOT NULL, "
                    + "type TEXT NOT NULL, "
                    + "currency TEXT, "
                    + "current_price REAL NOT NULL)");

            // Le portefeuille appartient à l'entreprise, pas à un utilisateur.
            st.execute("CREATE TABLE IF NOT EXISTS holdings ("
                    + "company_id INTEGER NOT NULL REFERENCES companies(id) ON DELETE CASCADE, "
                    + "symbol TEXT NOT NULL REFERENCES assets(symbol), "
                    + "quantity INTEGER NOT NULL CHECK (quantity > 0), "
                    + "PRIMARY KEY (company_id, symbol))");

            // Historique des prix de clôture : une ligne par actif et par jour.
            st.execute("CREATE TABLE IF NOT EXISTS price_history ("
                    + "symbol TEXT NOT NULL REFERENCES assets(symbol) ON DELETE CASCADE, "
                    + "date TEXT NOT NULL, " // format ISO : 2026-10-30
                    + "close_price REAL NOT NULL CHECK (close_price >= 0), "
                    + "PRIMARY KEY (symbol, date))");
        }
    }

    // ---------- Entreprises ----------

    public Company createCompany(String name, String siren, String sector) {
        // Company temporaire (id 0) pour réutiliser ses validations.
        Company draft = new Company(0, name, siren, sector);
        if (findCompanyBySiren(draft.getSiren()).isPresent()) {
            throw new IllegalArgumentException("Une entreprise existe déjà avec ce SIREN.");
        }

        execute("INSERT INTO companies (name, siren, sector) VALUES (?, ?, ?)",
                draft.getName(), draft.getSiren(), draft.getSector());
        return new Company(lastInsertId(), draft.getName(), draft.getSiren(), draft.getSector());
    }

    public Optional<Company> findCompanyById(int id) {
        return query("SELECT * FROM companies WHERE id = ?", this::readCompany, id)
                .stream().findFirst();
    }

    public Optional<Company> findCompanyBySiren(String siren) {
        if (siren == null) {
            return Optional.empty();
        }
        return query("SELECT * FROM companies WHERE siren = ?", this::readCompany, siren.trim())
                .stream().findFirst();
    }

    public List<Company> getAllCompanies() {
        return query("SELECT * FROM companies ORDER BY id", this::readCompany);
    }

    // Supprime l'entreprise, ses utilisateurs, ses comptes et son portefeuille.
    public void deleteCompany(int companyId) {
        getExistingCompany(companyId);
        execute("DELETE FROM companies WHERE id = ?", companyId);
    }

    // ---------- Utilisateurs ----------

    public user createUser(int companyId, String username, String email, String passwordHash) {
        getExistingCompany(companyId);

        // user temporaire (id 0) pour réutiliser ses validations et sa normalisation.
        user draft = new user(0, companyId, username, email, passwordHash);
        if (findUserByEmail(draft.getEmail()).isPresent()) {
            throw new IllegalArgumentException("Un compte existe déjà avec cet email.");
        }

        execute("INSERT INTO users (company_id, username, email, password_hash) VALUES (?, ?, ?, ?)",
                companyId, draft.getUsername(), draft.getEmail(), draft.getPasswordHash());
        return new user(lastInsertId(), companyId, draft.getUsername(),
                draft.getEmail(), draft.getPasswordHash());
    }

    public Optional<user> findUserById(int id) {
        return query("SELECT * FROM users WHERE id = ?", this::readUser, id)
                .stream().findFirst();
    }

    public Optional<user> findUserByEmail(String email) {
        return query("SELECT * FROM users WHERE email = ?", this::readUser, normalizeEmail(email))
                .stream().findFirst();
    }

    public List<user> getUsersOfCompany(int companyId) {
        return query("SELECT * FROM users WHERE company_id = ? ORDER BY id", this::readUser, companyId);
    }

    public void updateUserEmail(int userId, String newEmail) {
        user u = getExistingUser(userId);
        u.setEmail(newEmail); // valide et normalise

        Optional<user> owner = findUserByEmail(u.getEmail());
        if (owner.isPresent() && owner.get().getId() != userId) {
            throw new IllegalArgumentException("Un compte existe déjà avec cet email.");
        }
        execute("UPDATE users SET email = ? WHERE id = ?", u.getEmail(), userId);
    }

    // Utilisé par AuthService : on ne reçoit jamais le mot de passe en clair.
    public void updateUserPasswordHash(int userId, String passwordHash) {
        user u = getExistingUser(userId);
        u.setPasswordHash(passwordHash); // valide
        execute("UPDATE users SET password_hash = ? WHERE id = ?", u.getPasswordHash(), userId);
    }

    // Supprime seulement l'utilisateur : le portefeuille reste à l'entreprise.
    public void deleteUser(int userId) {
        getExistingUser(userId);
        execute("DELETE FROM users WHERE id = ?", userId);
    }

    // ---------- Comptes annuels ----------

    // Ajoute les comptes d'une année, ou les remplace s'ils existent déjà.
    public void saveFinancialStatement(FinancialStatement fs) {
        Objects.requireNonNull(fs, "Les comptes ne peuvent pas être nuls.");
        getExistingCompany(fs.getCompanyId());

        execute("INSERT INTO financial_statements (company_id, year, revenue, net_income, "
                + "annual_expenses, equity, total_debt, current_assets, current_liabilities, cash) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
                + "ON CONFLICT(company_id, year) DO UPDATE SET "
                + "revenue = excluded.revenue, net_income = excluded.net_income, "
                + "annual_expenses = excluded.annual_expenses, equity = excluded.equity, "
                + "total_debt = excluded.total_debt, current_assets = excluded.current_assets, "
                + "current_liabilities = excluded.current_liabilities, cash = excluded.cash",
                fs.getCompanyId(), fs.getYear(), fs.getRevenue(), fs.getNetIncome(),
                fs.getAnnualExpenses(), fs.getEquity(), fs.getTotalDebt(),
                fs.getCurrentAssets(), fs.getCurrentLiabilities(), fs.getCash());
    }

    public Optional<FinancialStatement> findFinancialStatement(int companyId, int year) {
        return query("SELECT * FROM financial_statements WHERE company_id = ? AND year = ?",
                this::readFinancialStatement, companyId, year).stream().findFirst();
    }

    // Tous les exercices de l'entreprise, du plus ancien au plus récent.
    public List<FinancialStatement> getFinancialStatements(int companyId) {
        return query("SELECT * FROM financial_statements WHERE company_id = ? ORDER BY year",
                this::readFinancialStatement, companyId);
    }

    public Optional<FinancialStatement> getLatestFinancialStatement(int companyId) {
        return query("SELECT * FROM financial_statements WHERE company_id = ? "
                + "ORDER BY year DESC LIMIT 1", this::readFinancialStatement, companyId)
                .stream().findFirst();
    }

    // ---------- Actifs ----------

    // Ajoute l'actif, ou met à jour ses infos s'il existe déjà.
    public void saveAsset(Asset asset) {
        Objects.requireNonNull(asset, "L'actif ne peut pas être nul.");
        execute("INSERT INTO assets (symbol, name, type, currency, current_price) "
                + "VALUES (?, ?, ?, ?, ?) "
                + "ON CONFLICT(symbol) DO UPDATE SET name = excluded.name, "
                + "type = excluded.type, currency = excluded.currency, "
                + "current_price = excluded.current_price",
                asset.getSymbol(), asset.getName(), asset.getType(),
                asset.getCurrency(), asset.getCurrentPrice());
    }

    public Optional<Asset> findAssetBySymbol(String symbol) {
        if (symbol == null) {
            return Optional.empty();
        }
        return query("SELECT * FROM assets WHERE symbol = ?", this::readAsset,
                symbol.trim().toUpperCase()).stream().findFirst();
    }

    public List<Asset> getAllAssets() {
        return query("SELECT * FROM assets ORDER BY symbol", this::readAsset);
    }

    // ---------- Portefeuilles ----------

    // Reconstruit le portefeuille de l'entreprise à partir de la table holdings.
    public portfolio getPortfolio(int companyId) {
        getExistingCompany(companyId);
        String sql = "SELECT a.*, h.quantity FROM holdings h "
                + "JOIN assets a ON a.symbol = h.symbol "
                + "WHERE h.company_id = ? ORDER BY a.symbol";

        portfolio p = new portfolio();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, companyId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    p.addAsset(readAsset(rs), rs.getInt("quantity"));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Erreur de lecture du portefeuille.", e);
        }
        return p;
    }

    /*
     * Remplace les positions enregistrées par celles du portefeuille.
     * Transaction : soit tout est enregistré, soit rien.
     */
    public void savePortfolio(int companyId, portfolio p) {
        getExistingCompany(companyId);
        Objects.requireNonNull(p, "Le portefeuille ne peut pas être nul.");

        inTransaction(() -> {
            execute("DELETE FROM holdings WHERE company_id = ?", companyId);
            for (Map.Entry<Asset, Integer> entry : p.getAssets().entrySet()) {
                saveAsset(entry.getKey());
                execute("INSERT INTO holdings (company_id, symbol, quantity) VALUES (?, ?, ?)",
                        companyId, entry.getKey().getSymbol(), entry.getValue());
            }
        });
    }

    // ---------- Historique des prix ----------

    /*
     * Enregistre des prix de clôture (date -> prix) pour un actif du catalogue.
     * Un prix déjà présent pour la même date est remplacé.
     */
    public void savePriceHistory(String symbol, Map<LocalDate, Double> closes) {
        Asset asset = findAssetBySymbol(symbol).orElseThrow(
                () -> new IllegalArgumentException("Actif inconnu : " + symbol));
        Objects.requireNonNull(closes, "Les prix ne peuvent pas être nuls.");

        inTransaction(() -> {
            for (Map.Entry<LocalDate, Double> entry : closes.entrySet()) {
                execute("INSERT INTO price_history (symbol, date, close_price) VALUES (?, ?, ?) "
                        + "ON CONFLICT(symbol, date) DO UPDATE SET close_price = excluded.close_price",
                        asset.getSymbol(), entry.getKey().toString(), entry.getValue());
            }
        });
    }

    // Prix de clôture d'un actif, triés du plus ancien au plus récent.
    public TreeMap<LocalDate, Double> getPriceHistory(String symbol) {
        TreeMap<LocalDate, Double> history = new TreeMap<>();
        if (symbol == null) {
            return history;
        }
        String sql = "SELECT date, close_price FROM price_history WHERE symbol = ? ORDER BY date";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, symbol.trim().toUpperCase());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    history.put(LocalDate.parse(rs.getString("date")), rs.getDouble("close_price"));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Erreur de lecture des prix.", e);
        }
        return history;
    }

    @Override
    public void close() {
        try {
            connection.close();
        } catch (SQLException e) {
            throw new IllegalStateException("Erreur à la fermeture de la base.", e);
        }
    }

    // ---------- Outils internes ----------

    /*
     * Décrit comment transformer une ligne de résultat SQL en objet Java.
     * Exemple : readCompany, readUser, readAsset...
     */
    private interface RowMapper<T> {
        T map(ResultSet rs) throws SQLException;
    }

    /*
     * Exécute plusieurs écritures comme un seul bloc :
     * soit tout est enregistré, soit rien (rollback en cas d'erreur).
     */
    private void inTransaction(Runnable work) {
        try {
            connection.setAutoCommit(false);
            try {
                work.run();
                connection.commit();
            } catch (RuntimeException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Erreur lors de la transaction.", e);
        }
    }

    // Exécute un INSERT / UPDATE / DELETE avec des paramètres "?".
    private void execute(String sql, Object... params) {
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            bind(ps, params);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Erreur SQL : " + e.getMessage(), e);
        }
    }

    // Exécute un SELECT et transforme chaque ligne avec le mapper donné.
    private <T> List<T> query(String sql, RowMapper<T> mapper, Object... params) {
        List<T> result = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            bind(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(mapper.map(rs));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Erreur de lecture : " + e.getMessage(), e);
        }
        return result;
    }

    // Les "?" protègent contre les injections SQL : ne jamais concaténer les valeurs.
    private void bind(PreparedStatement ps, Object... params) throws SQLException {
        for (int i = 0; i < params.length; i++) {
            ps.setObject(i + 1, params[i]);
        }
    }

    // Récupère l'id que SQLite vient d'attribuer au dernier INSERT.
    private int lastInsertId() {
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery("SELECT last_insert_rowid()")) {
            rs.next();
            return rs.getInt(1);
        } catch (SQLException e) {
            throw new IllegalStateException("Impossible de récupérer l'id créé.", e);
        }
    }

    private Company readCompany(ResultSet rs) throws SQLException {
        return new Company(rs.getInt("id"), rs.getString("name"),
                rs.getString("siren"), rs.getString("sector"));
    }

    private user readUser(ResultSet rs) throws SQLException {
        return new user(rs.getInt("id"), rs.getInt("company_id"), rs.getString("username"),
                rs.getString("email"), rs.getString("password_hash"));
    }

    private FinancialStatement readFinancialStatement(ResultSet rs) throws SQLException {
        return new FinancialStatement(rs.getInt("company_id"), rs.getInt("year"),
                rs.getDouble("revenue"), rs.getDouble("net_income"),
                rs.getDouble("annual_expenses"), rs.getDouble("equity"),
                rs.getDouble("total_debt"), rs.getDouble("current_assets"),
                rs.getDouble("current_liabilities"), rs.getDouble("cash"));
    }

    private Asset readAsset(ResultSet rs) throws SQLException {
        return new Asset(rs.getString("symbol"), rs.getString("name"),
                rs.getString("type"), rs.getString("currency"),
                rs.getDouble("current_price"));
    }

    private Company getExistingCompany(int companyId) {
        return findCompanyById(companyId).orElseThrow(
                () -> new IllegalArgumentException("Entreprise introuvable : " + companyId));
    }

    private user getExistingUser(int userId) {
        return findUserById(userId).orElseThrow(
                () -> new IllegalArgumentException("Utilisateur introuvable : " + userId));
    }

    private String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("L'email est obligatoire.");
        }
        return email.trim().toLowerCase();
    }
}
