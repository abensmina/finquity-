package com.finquity.api;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/*
 * Transforme les exceptions des services en réponses HTTP lisibles par le frontend :
 * { "error": "message" } avec le bon code.
 */
@RestControllerAdvice
public class ApiErrorHandler {

    // Données invalides (quantité négative, email déjà pris...) : 400
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> badRequest(IllegalArgumentException e) {
        return error(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    // Pas connecté : 401
    @ExceptionHandler(Session.NotLoggedInException.class)
    public ResponseEntity<Map<String, String>> unauthorized(Session.NotLoggedInException e) {
        return error(HttpStatus.UNAUTHORIZED, e.getMessage());
    }

    // Problème technique (Twelve Data, Python, base...) : 500
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, String>> serverError(IllegalStateException e) {
        return error(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
    }

    private static ResponseEntity<Map<String, String>> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of("error", message == null ? "Erreur" : message));
    }
}
