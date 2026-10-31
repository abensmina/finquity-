package com.finquity.api;

import com.finquity.data.DataBase;
import com.finquity.model.user;
import jakarta.servlet.http.HttpSession;

/*
 * Mémorise qui est connecté.
 * Après le login, Spring envoie au navigateur un cookie (JSESSIONID) ;
 * à chaque requête suivante, on retrouve l'id de l'utilisateur dans la session.
 */
public final class Session {

    private static final String USER_ID = "userId";

    private Session() {
    }

    public static void login(HttpSession session, user u) {
        session.setAttribute(USER_ID, u.getId());
    }

    public static void logout(HttpSession session) {
        session.invalidate();
    }

    // L'utilisateur connecté, ou une erreur 401 si personne ne l'est.
    public static user currentUser(HttpSession session, DataBase db) {
        Object id = session.getAttribute(USER_ID);
        if (!(id instanceof Integer userId)) {
            throw new NotLoggedInException();
        }
        return db.findUserById(userId).orElseThrow(NotLoggedInException::new);
    }

    public static class NotLoggedInException extends RuntimeException {
        public NotLoggedInException() {
            super("Vous devez être connecté.");
        }
    }
}
