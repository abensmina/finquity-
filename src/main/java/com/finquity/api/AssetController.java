package com.finquity.api;

import com.finquity.api.Dto.AssetRequest;
import com.finquity.data.DataBase;
import com.finquity.model.Asset;
import com.finquity.service.PriceService;
import jakarta.servlet.http.HttpSession;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// Le catalogue d'actifs (commun à toutes les PME) et leurs prix.
@RestController
@RequestMapping("/api/assets")
public class AssetController {

    private final DataBase db;
    private final PriceService prices;

    public AssetController(DataBase db, PriceService prices) {
        this.db = db;
        this.prices = prices;
    }

    // GET /api/assets
    @GetMapping
    public List<Asset> assets(HttpSession session) {
        Session.currentUser(session, db);
        return db.getAllAssets();
    }

    // POST /api/assets : ajoute (ou met à jour) un actif du catalogue.
    @PostMapping
    public Asset addAsset(@RequestBody AssetRequest req, HttpSession session) {
        Session.currentUser(session, db);
        Asset asset = new Asset(req.symbol(), req.name(), req.type(), req.currency(), req.currentPrice());
        db.saveAsset(asset);
        return asset;
    }

    // GET /api/assets/AAPL/prices : historique { "2026-10-29": 229.5, ... }
    @GetMapping("/{symbol}/prices")
    public Map<LocalDate, Double> history(@PathVariable String symbol, HttpSession session) {
        Session.currentUser(session, db);
        return prices.getHistory(symbol);
    }

    // POST /api/assets/AAPL/prices/update?days=750 : récupère les prix chez Twelve Data.
    @PostMapping("/{symbol}/prices/update")
    public Asset updatePrices(@PathVariable String symbol,
                              @RequestParam(defaultValue = "750") int days,
                              HttpSession session) {
        Session.currentUser(session, db);
        prices.updatePrices(symbol, days);
        return db.findAssetBySymbol(symbol).orElseThrow();
    }

    // POST /api/assets/prices/update-all : tout le catalogue (8 s par actif en offre gratuite).
    @PostMapping("/prices/update-all")
    public Map<String, String> updateAll(@RequestParam(defaultValue = "750") int days,
                                         HttpSession session) {
        Session.currentUser(session, db);
        return prices.updateAllPrices(days);
    }
}
