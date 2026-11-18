package com.finquity.ui;

// Erreur renvoyée par le serveur (ou serveur injoignable), avec un message lisible pour l'utilisateur.
public class ApiException extends Exception {

    // Code HTTP de la réponse (0 si le serveur n'a pas répondu).
    private final int status;

    public ApiException(String message) {
        this(message, 0);
    }

    public ApiException(String message, int status) {
        super(message);
        this.status = status;
    }

    // 401 : la session n'existe plus (serveur redémarré) ou on n'est pas connecté.
    public boolean isNotLoggedIn() {
        return status == 401;
    }
}
