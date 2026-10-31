package com.finquity.service;

import com.finquity.data.DataBase;
import com.finquity.model.FinancialStatement;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

/*
 * Importe les comptes annuels d'une PME depuis un fichier Excel.
 *
 * Format attendu (voir modele-comptes-annuels.xlsx) : première feuille,
 * ligne 1 = titres, puis une ligne par année, colonnes A à I :
 * Année | Chiffre d'affaires | Résultat net | Charges annuelles | Capitaux propres |
 * Dettes financières | Actif circulant | Passif circulant | Trésorerie
 */
public class ImportService {

    private static final int COLUMNS = 9;
    private static final String[] COLUMN_NAMES = {
            "Année", "Chiffre d'affaires", "Résultat net", "Charges annuelles", "Capitaux propres",
            "Dettes financières", "Actif circulant", "Passif circulant", "Trésorerie"};

    private final DataBase db;

    public ImportService(DataBase db) {
        this.db = Objects.requireNonNull(db, "La base de données est obligatoire.");
    }

    /*
     * Lit tout le fichier, vérifie chaque ligne, puis enregistre.
     * Si une seule ligne est fausse, rien n'est enregistré :
     * l'utilisateur corrige son fichier et recommence.
     * Une année déjà présente en base est remplacée.
     */
    public List<FinancialStatement> importStatements(int companyId, InputStream excelFile) {
        Objects.requireNonNull(excelFile, "Le fichier est obligatoire.");
        List<FinancialStatement> statements = new ArrayList<>();
        Set<Integer> years = new HashSet<>();

        try (Workbook workbook = WorkbookFactory.create(excelFile)) {
            Sheet sheet = workbook.getSheetAt(0);

            // Ligne 0 = titres : on commence à la ligne 1.
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (isEmpty(row)) {
                    continue; // on ignore les lignes vides
                }
                FinancialStatement fs = readRow(row, companyId);
                if (!years.add(fs.getYear())) {
                    throw new IllegalArgumentException(
                            "Ligne " + (i + 1) + " : l'année " + fs.getYear() + " apparaît deux fois.");
                }
                statements.add(fs);
            }
        } catch (IOException | RuntimeException e) {
            if (e instanceof IllegalArgumentException) {
                throw (IllegalArgumentException) e;
            }
            throw new IllegalArgumentException(
                    "Fichier illisible : envoie un fichier Excel (.xlsx) au format du modèle.", e);
        }

        if (statements.isEmpty()) {
            throw new IllegalArgumentException("Le fichier ne contient aucune année.");
        }

        // Tout est valide : on enregistre.
        for (FinancialStatement fs : statements) {
            db.saveFinancialStatement(fs);
        }
        return statements;
    }

    // Transforme une ligne du fichier en FinancialStatement.
    private FinancialStatement readRow(Row row, int companyId) {
        double[] v = new double[COLUMNS];
        for (int col = 0; col < COLUMNS; col++) {
            v[col] = readNumber(row, col);
        }

        int year = (int) v[0];
        if (year != v[0]) {
            throw new IllegalArgumentException(where(row, 0) + "l'année doit être un nombre entier.");
        }

        try {
            return new FinancialStatement(companyId, year,
                    v[1], v[2], v[3], v[4], v[5], v[6], v[7], v[8]);
        } catch (IllegalArgumentException e) {
            // Ajoute le numéro de ligne au message de FinancialStatement.
            throw new IllegalArgumentException("Ligne " + (row.getRowNum() + 1) + " : " + e.getMessage());
        }
    }

    /*
     * Lit la cellule n° col et renvoie sa valeur.
     * Accepte aussi un nombre tapé comme du texte ("800 000", "1 250,50 €").
     */
    private double readNumber(Row row, int col) {
        Cell cell = row.getCell(col);
        if (cell == null) {
            throw new IllegalArgumentException(where(row, col) + "case vide.");
        }

        CellType type = cell.getCellType();
        if (type == CellType.FORMULA) {
            type = cell.getCachedFormulaResultType(); // résultat déjà calculé par Excel
        }

        if (type == CellType.NUMERIC) {
            return cell.getNumericCellValue();
        }
        if (type == CellType.STRING) {
            String text = cell.getStringCellValue()
                    .replace("€", "")
                    .replaceAll("[\\s\\u00A0\\u202F]", "") // espaces, y compris insécables
                    .replace(',', '.');
            try {
                return Double.parseDouble(text);
            } catch (NumberFormatException e) {
                // on tombe sur l'erreur ci-dessous
            }
        }
        throw new IllegalArgumentException(where(row, col) + "nombre attendu.");
    }

    // Une ligne est vide si toutes ses cases le sont.
    private boolean isEmpty(Row row) {
        if (row == null) {
            return true;
        }
        for (int col = 0; col < COLUMNS; col++) {
            Cell cell = row.getCell(col);
            if (cell != null && cell.getCellType() != CellType.BLANK
                    && !(cell.getCellType() == CellType.STRING && cell.getStringCellValue().isBlank())) {
                return false;
            }
        }
        return true;
    }

    // "Ligne 3, colonne B (Résultat net) : " tel que l'utilisateur le voit dans Excel.
    private String where(Row row, int col) {
        return "Ligne " + (row.getRowNum() + 1) + ", colonne " + (char) ('A' + col)
                + " (" + COLUMN_NAMES[col] + ") : ";
    }
}