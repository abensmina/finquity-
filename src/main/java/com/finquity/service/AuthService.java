package com.finquity.service;

import com.finquity.data.DataBase;
import com.finquity.model.*;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;
import java.util.Objects;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/*
 * Inscription, connexion et mot de passe.
 *
 * Le mot de passe n'est jamais stocké : on stocke un "hash" PBKDF2,
 * calculé avec un sel aléatoire propre à chaque utilisateur.
 * Format stocké : pbkdf2$<itérations>$<sel en Base64>$<hash en Base64>
 */
public class AuthService {

    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final int ITERATIONS = 210_000; // lent exprès, pour freiner les attaques
    private static final int SALT_BYTES = 16;
    private static final int HASH_BITS = 256;

    // Message volontairement vague : on ne dit pas si c'est l'email ou le mot de passe.
    private static final String LOGIN_ERROR = "Email ou mot de passe incorrect.";

    private final DataBase db;
    private final SecureRandom random = new SecureRandom();

    public AuthService(DataBase db) {
        this.db = Objects.requireNonNull(db, "La base de données est obligatoire.");
    }

    // ---------- Inscription ----------

    /*
     * Inscrit une nouvelle PME et son premier utilisateur.
     * Si la création de l'utilisateur échoue, l'entreprise est supprimée
     * pour ne pas laisser d'entreprise sans compte.
     */
    public user registerCompany(String companyName, String siren, String sector,
                                String username, String email, String password) {
        checkPasswordStrength(password);
        if (db.findUserByEmail(email).isPresent()) {
            throw new IllegalArgumentException("Un compte existe déjà avec cet email.");
        }

        Company company = db.createCompany(companyName, siren, sector);
        try {
            return db.createUser(company.getId(), username, email, hashPassword(password));
        } catch (RuntimeException e) {
            db.deleteCompany(company.getId());
            throw e;
        }
    }

    // Ajoute un collègue à une PME déjà inscrite.
    public user registerUser(int companyId, String username, String email, String password) {
        checkPasswordStrength(password);
        return db.createUser(companyId, username, email, hashPassword(password));
    }

    // ---------- Connexion ----------

    // Retourne l'utilisateur si l'email et le mot de passe sont corrects.
    public user login(String email, String password) {
        if (email == null || email.isBlank() || password == null) {
            throw new IllegalArgumentException(LOGIN_ERROR);
        }

        user u = db.findUserByEmail(email).orElseThrow(
                () -> new IllegalArgumentException(LOGIN_ERROR));

        if (!verifyPassword(password, u.getPasswordHash())) {
            throw new IllegalArgumentException(LOGIN_ERROR);
        }
        return u;
    }

    // ---------- Mot de passe ----------

    public void changePassword(int userId, String oldPassword, String newPassword) {
        user u = db.findUserById(userId).orElseThrow(
                () -> new IllegalArgumentException("Utilisateur introuvable : " + userId));

        if (oldPassword == null || !verifyPassword(oldPassword, u.getPasswordHash())) {
            throw new IllegalArgumentException("L'ancien mot de passe est incorrect.");
        }
        checkPasswordStrength(newPassword);
        db.updateUserPasswordHash(userId, hashPassword(newPassword));
    }

    // Au moins 8 caractères, dont une lettre et un chiffre.
    private void checkPasswordStrength(String password) {
        if (password == null || password.length() < 8) {
            throw new IllegalArgumentException(
                    "Le mot de passe doit contenir au moins 8 caractères.");
        }
        if (!password.matches(".*[A-Za-z].*") || !password.matches(".*\\d.*")) {
            throw new IllegalArgumentException(
                    "Le mot de passe doit contenir au moins une lettre et un chiffre.");
        }
    }

    // ---------- Hachage ----------

    private String hashPassword(String password) {
        byte[] salt = new byte[SALT_BYTES];
        random.nextBytes(salt);
        byte[] hash = pbkdf2(password, salt, ITERATIONS);

        Base64.Encoder b64 = Base64.getEncoder();
        return "pbkdf2$" + ITERATIONS + "$" + b64.encodeToString(salt) + "$" + b64.encodeToString(hash);
    }

    // Recalcule le hash avec le même sel et compare.
    private boolean verifyPassword(String password, String stored) {
        String[] parts = stored.split("\\$");
        if (parts.length != 4 || !parts[0].equals("pbkdf2")) {
            return false;
        }

        int iterations = Integer.parseInt(parts[1]);
        byte[] salt = Base64.getDecoder().decode(parts[2]);
        byte[] expected = Base64.getDecoder().decode(parts[3]);
        byte[] actual = pbkdf2(password, salt, iterations);

        // Comparaison en temps constant : ne révèle pas où les octets diffèrent.
        return MessageDigest.isEqual(expected, actual);
    }

    private byte[] pbkdf2(String password, byte[] salt, int iterations) {
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations, HASH_BITS);
        try {
            return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).getEncoded();
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IllegalStateException("Hachage du mot de passe impossible.", e);
        } finally {
            spec.clearPassword();
        }
    }
}
