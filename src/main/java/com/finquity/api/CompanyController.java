package com.finquity.api;

import com.finquity.api.Dto.CompanyView;
import com.finquity.api.Dto.StatementRequest;
import com.finquity.api.Dto.UserView;
import com.finquity.data.DataBase;
import com.finquity.model.CompanyDiagnosis;
import com.finquity.model.FinancialStatement;
import com.finquity.service.AnalysisService;
import jakarta.servlet.http.HttpSession;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// La PME de l'utilisateur connecté : infos, comptes annuels, diagnostic.
@RestController
@RequestMapping("/api/company")
public class CompanyController {

    private final DataBase db;
    private final AnalysisService analysis;

    public CompanyController(DataBase db, AnalysisService analysis) {
        this.db = db;
        this.analysis = analysis;
    }

    // GET /api/company
    @GetMapping
    public CompanyView company(HttpSession session) {
        int companyId = companyId(session);
        return CompanyView.of(db.findCompanyById(companyId).orElseThrow());
    }

    // GET /api/company/users
    @GetMapping("/users")
    public List<UserView> users(HttpSession session) {
        return db.getUsersOfCompany(companyId(session)).stream().map(UserView::of).toList();
    }

    // GET /api/company/statements : tous les exercices, du plus ancien au plus récent.
    @GetMapping("/statements")
    public List<FinancialStatement> statements(HttpSession session) {
        return db.getFinancialStatements(companyId(session));
    }

    // POST /api/company/statements : ajoute (ou remplace) les comptes d'une année.
    @PostMapping("/statements")
    public FinancialStatement saveStatement(@RequestBody StatementRequest req, HttpSession session) {
        FinancialStatement fs = new FinancialStatement(companyId(session), req.year(),
                req.revenue(), req.netIncome(), req.annualExpenses(), req.equity(), req.totalDebt(),
                req.currentAssets(), req.currentLiabilities(), req.cash());
        db.saveFinancialStatement(fs);
        return fs;
    }

    // GET /api/company/diagnosis : ratios, score, profil, montant plaçable.
    @GetMapping("/diagnosis")
    public CompanyDiagnosis diagnosis(HttpSession session) {
        return analysis.diagnose(companyId(session));
    }

    private int companyId(HttpSession session) {
        return Session.currentUser(session, db).getCompanyId();
    }
}
