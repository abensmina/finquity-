package com.finquity.api;

import com.finquity.data.DataBase;
import com.finquity.service.ReportsService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Le rapport PDF de la PME connectée.
@RestController
@RequestMapping("/api/company")
public class ReportController {

    private final DataBase db;
    private final ReportsService reports;

    public ReportController(DataBase db, ReportsService reports) {
        this.db = db;
        this.reports = reports;
    }

    // GET /api/company/report : renvoie le PDF.
    @GetMapping("/report")
    public ResponseEntity<byte[]> report(HttpSession session) {
        int companyId = Session.currentUser(session, db).getCompanyId();
        byte[] pdf = reports.generateReport(companyId);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"rapport-finquity.pdf\"")
                .body(pdf);
    }
}