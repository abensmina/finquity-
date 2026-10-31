package com.finquity.model;

public class user {
    private final int id;
    private final int companyId; // la PME à laquelle appartient l'utilisateur
    private String username;
    private String email;
    private String passwordHash;

    public user(int id, int companyId, String username, String email, String passwordHash) {
        if (id < 0) {
            throw new IllegalArgumentException("L'identifiant doit être positif ou nul.");
        }
        if (companyId <= 0) {
            throw new IllegalArgumentException("L'utilisateur doit appartenir à une entreprise.");
        }
        // On réutilise les setters pour ne pas dupliquer les validations.
        this.id = id;
        this.companyId = companyId;
        setUsername(username);
        setEmail(email);
        setPasswordHash(passwordHash);
    }

    public int getId() {
        return id;
    }

    public int getCompanyId() {
        return companyId;
    }

    public String getUsername() {
        return username;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setUsername(String username) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Le nom d'utilisateur est obligatoire.");
        }
        this.username = username.trim();
    }

    public void setEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("L'email est obligatoire.");
        }
        if (!email.contains("@")) {
            throw new IllegalArgumentException("L'email n'est pas valide.");
        }
        // On normalise l'email pour éviter les doublons de casse.
        this.email = email.trim().toLowerCase();
    }

    public void setPasswordHash(String passwordHash) {
        if (passwordHash == null || passwordHash.isBlank()) {
            throw new IllegalArgumentException("Le mot de passe est obligatoire.");
        }
        this.passwordHash = passwordHash;
    }

    // Le mot de passe n'apparaît pas dans toString(), volontairement.
    @Override
    public String toString() {
        return "User{" +
                "id=" + id +
                ", companyId=" + companyId +
                ", username='" + username + '\'' +
                ", email='" + email + '\'' +
                '}';
    }
}
