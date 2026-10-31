package com.finquity.api;

import com.finquity.api.Dto.PortfolioView;
import com.finquity.api.Dto.TradeRequest;
import com.finquity.data.DataBase;
import com.finquity.model.SimulationResult;
import com.finquity.service.SimulationService;
import com.finquity.service.portfolioService;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// Le portefeuille de la PME connectée.
@RestController
@RequestMapping("/api/portfolio")
public class PortfolioController {

    private final DataBase db;
    private final portfolioService portfolios;
    private final SimulationService simulation;

    public PortfolioController(DataBase db, portfolioService portfolios, SimulationService simulation) {
        this.db = db;
        this.portfolios = portfolios;
        this.simulation = simulation;
    }

    // GET /api/portfolio : positions, valeur totale, poids de chaque actif.
    @GetMapping
    public PortfolioView portfolio(HttpSession session) {
        return PortfolioView.of(portfolios.getPortfolio(companyId(session)));
    }

    // POST /api/portfolio/buy  { "symbol": "AAPL", "quantity": 10 }
    @PostMapping("/buy")
    public PortfolioView buy(@RequestBody TradeRequest req, HttpSession session) {
        int companyId = companyId(session);
        portfolios.buyAsset(companyId, req.symbol(), req.quantity());
        return PortfolioView.of(portfolios.getPortfolio(companyId));
    }

    // POST /api/portfolio/sell  { "symbol": "AAPL", "quantity": 2 }
    @PostMapping("/sell")
    public PortfolioView sell(@RequestBody TradeRequest req, HttpSession session) {
        int companyId = companyId(session);
        portfolios.sellAsset(companyId, req.symbol(), req.quantity());
        return PortfolioView.of(portfolios.getPortfolio(companyId));
    }

    // GET /api/portfolio/simulation?years=5&simulations=5000 : Monte-Carlo.
    @GetMapping("/simulation")
    public SimulationResult simulate(@RequestParam(defaultValue = "5") int years,
                                     @RequestParam(defaultValue = "5000") int simulations,
                                     HttpSession session) {
        return simulation.simulate(companyId(session), years, simulations);
    }

    private int companyId(HttpSession session) {
        return Session.currentUser(session, db).getCompanyId();
    }
}
