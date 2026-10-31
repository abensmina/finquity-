package com.finquity.service;

import com.finquity.data.DataBase;
import com.finquity.model.Asset;
import com.finquity.model.Company;
import com.finquity.model.CompanyDiagnosis;
import com.finquity.model.FinancialStatement;
import com.finquity.model.RiskProfile;
import com.finquity.model.portfolio;
import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/*
 * Fabrique le rapport PDF d'une PME :
 *   1. l'entreprise
 *   2. ses comptes annuels
 *   3. le diagnostic (ratios, note, profil, alertes)
 *   4. le montant plaçable et les règles du profil
 *   5. le portefeuille actuel
 *
 * Ne lit rien lui-même : il demande les chiffres à DataBase et à AnalysisService,
 * puis se contente de les mettre en page. Renvoie le PDF sous forme d'octets.
 */
public class ReportsService {

    private static final Font TITLE = new Font(Font.HELVETICA, 20, Font.BOLD, new Color(20, 50, 90));
    private static final Font SECTION = new Font(Font.HELVETICA, 13, Font.BOLD, new Color(20, 50, 90));
    private static final Font NORMAL = new Font(Font.HELVETICA, 10);
    private static final Font BOLD = new Font(Font.HELVETICA, 10, Font.BOLD);
    private static final Font SMALL = new Font(Font.HELVETICA, 8, Font.ITALIC, Color.GRAY);
    private static final Font ALERT = new Font(Font.HELVETICA, 10, Font.NORMAL, new Color(180, 40, 40));
    private static final Color HEADER_BG = new Color(225, 232, 242);

    private final DataBase db;
    private final AnalysisService analysis;

    public ReportsService(DataBase db, AnalysisService analysis) {
        this.db = Objects.requireNonNull(db, "La base de données est obligatoire.");
        this.analysis = Objects.requireNonNull(analysis, "AnalysisService est obligatoire.");
    }

    public byte[] generateReport(int companyId) {
        Company company = db.findCompanyById(companyId).orElseThrow(
                () -> new IllegalArgumentException("Entreprise inconnue : " + companyId));
        List<FinancialStatement> statements = db.getFinancialStatements(companyId);
        if (statements.isEmpty()) {
            throw new IllegalArgumentException(
                    "Aucun compte annuel enregistré : importe ou saisis tes comptes avant de demander le rapport.");
        }
        CompanyDiagnosis diagnosis = analysis.diagnose(companyId);
        portfolio portfolio = db.getPortfolio(companyId);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 50, 50, 50, 50);
        try {
            PdfWriter.getInstance(doc, out);
            doc.open();
            addHeader(doc, company);
            addStatements(doc, statements);
            addDiagnosis(doc, diagnosis);
            addInvestable(doc, diagnosis);
            addPortfolio(doc, portfolio);
            addFooter(doc);
        } finally {
            doc.close();
        }
        return out.toByteArray();
    }

    // ---------- Sections ----------

    private void addHeader(Document doc, Company company) {
        doc.add(new Paragraph("Rapport financier Finquity", TITLE));
        doc.add(new Paragraph(company.getName(), BOLD));
        doc.add(new Paragraph("SIREN " + company.getSiren() + "   ·   Secteur : " + company.getSector(), NORMAL));
        doc.add(new Paragraph("Édité le "
                + LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")), SMALL));
    }

    private void addStatements(Document doc, List<FinancialStatement> statements) {
        doc.add(section("1. Comptes annuels"));

        // Les 3 derniers exercices au maximum, pour que le tableau tienne sur la page.
        List<FinancialStatement> shown = statements.subList(Math.max(0, statements.size() - 3), statements.size());

        PdfPTable table = new PdfPTable(shown.size() + 1);
        table.setWidthPercentage(100);
        table.addCell(headerCell("En euros"));
        for (FinancialStatement fs : shown) {
            table.addCell(headerCell(String.valueOf(fs.getYear())));
        }
        addRow(table, "Chiffre d'affaires", shown, FinancialStatement::getRevenue);
        addRow(table, "Résultat net", shown, FinancialStatement::getNetIncome);
        addRow(table, "Charges annuelles", shown, FinancialStatement::getAnnualExpenses);
        addRow(table, "Capitaux propres", shown, FinancialStatement::getEquity);
        addRow(table, "Dettes financières", shown, FinancialStatement::getTotalDebt);
        addRow(table, "Actif circulant", shown, FinancialStatement::getCurrentAssets);
        addRow(table, "Passif circulant", shown, FinancialStatement::getCurrentLiabilities);
        addRow(table, "Trésorerie", shown, FinancialStatement::getCash);
        doc.add(table);
    }

    private void addDiagnosis(Document doc, CompanyDiagnosis d) {
        doc.add(section("2. Diagnostic (exercice " + d.getYear() + ")"));

        Paragraph score = new Paragraph();
        score.add(new Chunk("Note de santé financière : ", NORMAL));
        score.add(new Chunk(d.getScore() + " / 100", BOLD));
        score.add(new Chunk("      Profil : ", NORMAL));
        score.add(new Chunk(d.getProfile().getLabel(), BOLD));
        score.setSpacingAfter(6);
        doc.add(score);

        PdfPTable table = new PdfPTable(new float[] {3, 1.4f, 4});
        table.setWidthPercentage(100);
        table.addCell(headerCell("Ratio"));
        table.addCell(headerCell("Valeur"));
        table.addCell(headerCell("Ce que ça mesure"));
        ratioRow(table, "Dettes / capitaux propres", times(d.getDebtToEquity()), "Solvabilité : poids des dettes");
        ratioRow(table, "Liquidité générale", times(d.getCurrentRatio()), "Capacité à payer les dettes à court terme");
        ratioRow(table, "Marge nette", percent(d.getNetMargin()), "Bénéfice sur 100 € de ventes");
        ratioRow(table, "Rentabilité des fonds propres", percent(d.getReturnOnEquity()), "Bénéfice rapporté aux capitaux propres");
        ratioRow(table, "Croissance du CA", percent(d.getRevenueGrowth()), "Évolution des ventes sur un an");
        doc.add(table);

        if (!d.getAlerts().isEmpty()) {
            Paragraph title = new Paragraph("Points d'attention", BOLD);
            title.setSpacingBefore(8);
            doc.add(title);
            for (String alert : d.getAlerts()) {
                doc.add(new Paragraph("•  " + alert, ALERT));
            }
        }
    }

    private void addInvestable(Document doc, CompanyDiagnosis d) {
        RiskProfile p = d.getProfile();
        doc.add(section("3. Combien placer ?"));

        PdfPTable table = new PdfPTable(new float[] {3, 2});
        table.setWidthPercentage(70);
        table.setHorizontalAlignment(Element.ALIGN_LEFT);
        labelValue(table, "Réserve de sécurité (" + p.getReserveMonths() + " mois de charges)", euros(d.getReserve()));
        labelValue(table, "Montant plaçable", euros(d.getInvestableAmount()));
        labelValue(table, "Part maximale en actions", percent(p.getMaxEquityShare()));
        labelValue(table, "Part minimale en placements sûrs", percent(p.getMinSafeShare()));
        doc.add(table);

        if (d.getInvestableAmount() <= 0) {
            doc.add(new Paragraph(
                    "La trésorerie ne couvre pas la réserve de sécurité : il est conseillé de ne rien placer pour l'instant.",
                    ALERT));
        }
    }

    private void addPortfolio(Document doc, portfolio portfolio) {
        doc.add(section("4. Portefeuille actuel"));

        Map<Asset, Integer> positions = portfolio.getAssets();
        if (positions.isEmpty()) {
            doc.add(new Paragraph("Aucun placement pour le moment.", NORMAL));
            return;
        }

        double total = portfolio.getTotalValue();
        PdfPTable table = new PdfPTable(new float[] {1.3f, 3, 1.5f, 1, 1.6f, 1});
        table.setWidthPercentage(100);
        for (String h : new String[] {"Symbole", "Nom", "Type", "Qté", "Valeur", "Poids"}) {
            table.addCell(headerCell(h));
        }
        for (Map.Entry<Asset, Integer> e : positions.entrySet()) {
            Asset a = e.getKey();
            double value = a.getCurrentPrice() * e.getValue();
            table.addCell(cell(a.getSymbol(), NORMAL, Element.ALIGN_LEFT));
            table.addCell(cell(a.getName(), NORMAL, Element.ALIGN_LEFT));
            table.addCell(cell(a.getType(), NORMAL, Element.ALIGN_LEFT));
            table.addCell(cell(String.valueOf(e.getValue()), NORMAL, Element.ALIGN_RIGHT));
            table.addCell(cell(money(value) + " " + a.getCurrency(), NORMAL, Element.ALIGN_RIGHT));
            table.addCell(cell(total > 0 ? percent(value / total) : "-", NORMAL, Element.ALIGN_RIGHT));
        }
        doc.add(table);

        Paragraph sum = new Paragraph();
        sum.add(new Chunk("Valeur totale : ", NORMAL));
        sum.add(new Chunk(money(total), BOLD));
        sum.setSpacingBefore(4);
        doc.add(sum);
    }

    private void addFooter(Document doc) {
        Paragraph p = new Paragraph(
                "Finquity est un outil d'aide à la décision. Ce rapport ne constitue pas un conseil en "
                        + "investissement : les performances passées ne préjugent pas des performances futures.",
                SMALL);
        p.setSpacingBefore(20);
        doc.add(p);
    }

    // ---------- Petits outils de mise en page ----------

    private Paragraph section(String text) {
        Paragraph p = new Paragraph(text, SECTION);
        p.setSpacingBefore(16);
        p.setSpacingAfter(6);
        return p;
    }

    private PdfPCell headerCell(String text) {
        PdfPCell c = cell(text, BOLD, Element.ALIGN_CENTER);
        c.setBackgroundColor(HEADER_BG);
        return c;
    }

    private PdfPCell cell(String text, Font font, int align) {
        PdfPCell c = new PdfPCell(new Phrase(text, font));
        c.setHorizontalAlignment(align);
        c.setPadding(4);
        return c;
    }

    private void addRow(PdfPTable table, String label, List<FinancialStatement> shown,
                        java.util.function.ToDoubleFunction<FinancialStatement> value) {
        table.addCell(cell(label, NORMAL, Element.ALIGN_LEFT));
        for (FinancialStatement fs : shown) {
            table.addCell(cell(money(value.applyAsDouble(fs)), NORMAL, Element.ALIGN_RIGHT));
        }
    }

    private void ratioRow(PdfPTable table, String name, String value, String meaning) {
        table.addCell(cell(name, NORMAL, Element.ALIGN_LEFT));
        table.addCell(cell(value, BOLD, Element.ALIGN_RIGHT));
        table.addCell(cell(meaning, NORMAL, Element.ALIGN_LEFT));
    }

    private void labelValue(PdfPTable table, String label, String value) {
        table.addCell(cell(label, NORMAL, Element.ALIGN_LEFT));
        table.addCell(cell(value, BOLD, Element.ALIGN_RIGHT));
    }

    // ---------- Formats (à la française) ----------

    private String money(double v) {
        NumberFormat f = NumberFormat.getNumberInstance(Locale.FRANCE);
        f.setMaximumFractionDigits(0);
        return f.format(v).replace(' ', ' ').replace(' ', ' ');
    }

    private String euros(double v) {
        return money(v) + " €";
    }

    // NaN = ratio sans signification (voir CompanyDiagnosis).
    private String percent(double v) {
        if (Double.isNaN(v)) {
            return "n.d.";
        }
        return String.format(Locale.FRANCE, "%.1f %%", v * 100);
    }

    private String times(double v) {
        if (Double.isNaN(v)) {
            return "n.d.";
        }
        return String.format(Locale.FRANCE, "%.2f", v);
    }
}