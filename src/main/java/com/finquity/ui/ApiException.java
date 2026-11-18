package com.finquity.ui;

// Erreur renvoyée par le serveur (ou serveur injoignable), avec un message lisible pour l'utilisateur.
public class ApiException extends Exception {

    public ApiException(String message) {
        super(message);
    }
}
