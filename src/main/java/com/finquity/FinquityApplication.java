package com.finquity;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/*
 * Point d'entrée du backend : lance le serveur sur http://localhost:8080
 * Commande : mvn spring-boot:run (depuis la racine du projet)
 */
@SpringBootApplication
public class FinquityApplication {

    public static void main(String[] args) {
        SpringApplication.run(FinquityApplication.class, args);
    }
}
