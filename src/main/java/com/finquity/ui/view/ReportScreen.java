package com.finquity.ui.view;

import com.finquity.ui.Navigator;
import java.io.File;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

// Rapport PDF : le serveur le génère (ReportsService) et on l'enregistre où l'on veut.
public class ReportScreen extends Screen {

    public ReportScreen(Navigator nav) {
        super(nav);
    }

    @Override
    protected String title() {
        return "Rapport PDF";
    }

    @Override
    protected String subtitle() {
        return "Document récapitulatif à télécharger";
    }

    @Override
    protected Node content() {
        Label t = new Label("Rapport de votre entreprise");
        t.getStyleClass().add("card-title");
        Label d = new Label("Le rapport reprend vos comptes des trois dernières années, le diagnostic "
                + "(note, profil de risque, alertes), le montant plaçable et votre portefeuille. "
                + "C'est un outil d'aide à la décision : il ne constitue pas un conseil en investissement.");
        d.getStyleClass().add("card-text");
        d.setWrapText(true);
        Button download = primaryButton("Télécharger le PDF");
        download.setOnAction(e -> download(download));
        VBox card = new VBox(16, t, d, download);
        card.getStyleClass().add("card");
        return new VBox(32, headline("Votre ", "rapport"), card);
    }

    private void download(Button owner) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Enregistrer le rapport");
        chooser.setInitialFileName("rapport-finquity.pdf");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF", "*.pdf"));
        File file = chooser.showSaveDialog(owner.getScene().getWindow());
        if (file == null) {
            return;
        }
        clearMessages();
        call(() -> {
            api().downloadReport(file.toPath());
            return file;
        }, saved -> showInfo("Rapport enregistré : " + saved.getAbsolutePath()));
    }
}
