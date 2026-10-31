package com.finquity.api;

import com.finquity.api.Dto.AddUserRequest;
import com.finquity.api.Dto.ChangePasswordRequest;
import com.finquity.api.Dto.LoginRequest;
import com.finquity.api.Dto.RegisterRequest;
import com.finquity.api.Dto.UserView;
import com.finquity.data.DataBase;
import com.finquity.model.user;
import com.finquity.service.AuthService;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Inscription, connexion, déconnexion.
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService auth;
    private final DataBase db;

    public AuthController(AuthService auth, DataBase db) {
        this.auth = auth;
        this.db = db;
    }

    // POST /api/auth/register : inscrit une PME et son premier utilisateur, puis le connecte.
    @PostMapping("/register")
    public UserView register(@RequestBody RegisterRequest req, HttpSession session) {
        user u = auth.registerCompany(req.companyName(), req.siren(), req.sector(),
                req.username(), req.email(), req.password());
        Session.login(session, u);
        return UserView.of(u);
    }

    // POST /api/auth/users : l'utilisateur connecté ajoute un collègue à sa PME.
    @PostMapping("/users")
    public UserView addColleague(@RequestBody AddUserRequest req, HttpSession session) {
        user me = Session.currentUser(session, db);
        return UserView.of(auth.registerUser(me.getCompanyId(), req.username(), req.email(), req.password()));
    }

    // POST /api/auth/login
    @PostMapping("/login")
    public UserView login(@RequestBody LoginRequest req, HttpSession session) {
        user u = auth.login(req.email(), req.password());
        Session.login(session, u);
        return UserView.of(u);
    }

    // POST /api/auth/logout
    @PostMapping("/logout")
    public void logout(HttpSession session) {
        Session.logout(session);
    }

    // GET /api/auth/me : qui est connecté ?
    @GetMapping("/me")
    public UserView me(HttpSession session) {
        return UserView.of(Session.currentUser(session, db));
    }

    // POST /api/auth/password
    @PostMapping("/password")
    public void changePassword(@RequestBody ChangePasswordRequest req, HttpSession session) {
        user me = Session.currentUser(session, db);
        auth.changePassword(me.getId(), req.oldPassword(), req.newPassword());
    }
}
