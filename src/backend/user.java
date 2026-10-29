public class user  {
    private int id;
    private String username;
    private String email;
    private String password;

    public user(int id, String username, String email, String passwordHash) {
        if (id < 0) {
            throw new IllegalArgumentException("L'identifiant doit être positif ou nul.");
        }
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Le nom d'utilisateur est obligatoire.");
        }
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("L'email est obligatoire.");
        }
        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException("Le mot de passe est obligatoire.");
        }

        this.username = username;
        this.id = id;
        this.email = email;
        this.password = password;
    }

    public String getUsername() {
        return username;
    }
    public int getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException("Le mot de passe est obligatoire.");
        }
        this.password = password;
    }
    public void setEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("L'email est obligatoire.");
        }
        this.email = email;
    }
    public void setUsername(String username) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("Le nom d'utilisateur est obligatoire.");
        }
        this.username = username;
    }
    @Override
    public String toString() {
        return "User{" +
                "id=" + id +
                ", username='" + username + '\'' +
                ", email='" + email + '\'' +
                '}';
}
}