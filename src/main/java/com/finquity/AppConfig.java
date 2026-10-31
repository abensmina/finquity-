package com.finquity;

import com.finquity.data.DataBase;
import com.finquity.service.ImportService;
import com.finquity.service.AnalysisService;
import com.finquity.service.AuthService;
import com.finquity.service.PriceProvider;
import com.finquity.service.PriceService;
import com.finquity.service.PythonRunner;
import com.finquity.service.SimulationService;
import com.finquity.service.TwelveDataProvider;
import com.finquity.service.portfolioService;
import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.finquity.service.ReportsService;

/*
 * Crée une seule fois chaque service et les relie entre eux.
 * Les services restent du Java normal : seule cette classe connaît Spring.
 * Les contrôleurs reçoivent ces objets automatiquement dans leur constructeur.
 */
@Configuration
public class AppConfig {

    // destroyMethod : Spring ferme la base proprement quand le serveur s'arrête.
    @Bean(destroyMethod = "close")
    public DataBase dataBase(@Value("${finquity.db.path}") String path) {
        return new DataBase(path);
    }

    @Bean
    public PythonRunner pythonRunner(@Value("${finquity.analytics.dir}") String dir) {
        return new PythonRunner(Path.of(dir));
    }

    /*
     * Sans clé Twelve Data, le serveur démarre quand même :
     * seule la mise à jour des prix renverra une erreur explicite.
     */
    @Bean
    public PriceProvider priceProvider() {
        String key = System.getenv("TWELVE_DATA_API_KEY");
        if (key == null || key.isBlank()) {
            return (symbol, days) -> {
                throw new IllegalStateException(
                        "Clé Twelve Data absente : définis la variable d'environnement TWELVE_DATA_API_KEY.");
            };
        }
        return new TwelveDataProvider(key);
    }

    @Bean
    public AuthService authService(DataBase db) {
        return new AuthService(db);
    }

    @Bean
    public portfolioService portfolioService(DataBase db) {
        return new portfolioService(db);
    }

    @Bean
    public AnalysisService analysisService(DataBase db) {
        return new AnalysisService(db);
    }
    
    @Bean
    public ReportsService reportsService(DataBase db, AnalysisService analysis) {
        return new ReportsService(db, analysis);
    }

    @Bean
    public PriceService priceService(DataBase db, PriceProvider provider) {
        return new PriceService(db, provider);
    }

    @Bean
    public SimulationService simulationService(DataBase db, PythonRunner python) {
        return new SimulationService(db, python);
    }
    
    @Bean
    public ImportService importService(DataBase db) {
        return new ImportService(db);
    }
}
