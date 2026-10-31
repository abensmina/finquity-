package com.finquity.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/*
 * Le pont entre Java et les scripts Python de src/analytics.
 *
 * Java envoie du JSON sur l'entrée du script et lit le JSON qu'il écrit en sortie.
 * C'est le seul endroit du backend qui lance Python.
 *
 * La commande Python peut être changée avec la variable d'environnement
 * FINQUITY_PYTHON (par défaut : python3).
 */
public class PythonRunner {

    private static final long TIMEOUT_SECONDS = 120;

    private final Path scriptsDir;
    private final String pythonCommand;

    // Par défaut : les scripts sont dans src/analytics, depuis la racine du projet.
    public PythonRunner() {
        this(Path.of("src", "analytics"));
    }

    public PythonRunner(Path scriptsDir) {
        this.scriptsDir = Objects.requireNonNull(scriptsDir, "Le dossier des scripts est obligatoire.");
        String fromEnv = System.getenv("FINQUITY_PYTHON");
        this.pythonCommand = (fromEnv == null || fromEnv.isBlank()) ? "python3" : fromEnv.trim();
    }

    // Lance le script avec `jsonInput` en entrée et renvoie ce qu'il a écrit en sortie.
    public String run(String scriptName, String jsonInput) {
        Path script = scriptsDir.resolve(scriptName);
        if (!Files.isRegularFile(script)) {
            throw new IllegalStateException("Script Python introuvable : " + script.toAbsolutePath());
        }

        Process process;
        try {
            process = new ProcessBuilder(pythonCommand, script.toString()).start();
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Impossible de lancer « " + pythonCommand + " ». Python est-il installé ?", e);
        }

        try {
            // Lecture des sorties en parallèle, pour que le script ne bloque jamais.
            CompletableFuture<String> stdout = readAsync(process.getInputStream());
            CompletableFuture<String> stderr = readAsync(process.getErrorStream());

            try (OutputStream in = process.getOutputStream()) {
                in.write(jsonInput.getBytes(StandardCharsets.UTF_8));
            }

            if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new IllegalStateException(scriptName + " a dépassé " + TIMEOUT_SECONDS + " s.");
            }
            if (process.exitValue() != 0) {
                throw new IllegalStateException(stderr.join().trim());
            }
            return stdout.join();
        } catch (IOException e) {
            throw new IllegalStateException("Erreur de communication avec " + scriptName + ".", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(scriptName + " a été interrompu.", e);
        }
    }

    private static CompletableFuture<String> readAsync(InputStream stream) {
        return CompletableFuture.supplyAsync(() -> {
            try (stream) {
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                stream.transferTo(buffer);
                return buffer.toString(StandardCharsets.UTF_8);
            } catch (IOException e) {
                return "";
            }
        });
    }

    // ---------- Petits outils JSON ----------
    // Suffisants pour nos échanges de nombres ; Spring Boot apportera Jackson plus tard.

    public static String toJson(double[] values) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(values[i]);
        }
        return sb.append(']').toString();
    }

    public static String toJson(double[][] matrix) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < matrix.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(toJson(matrix[i]));
        }
        return sb.append(']').toString();
    }

    private static final String NUMBER = "-?\\d+(?:\\.\\d+)?(?:[eE][-+]?\\d+)?";

    // Lit un nombre : "clé": 12.5
    public static double readDouble(String json, String key) {
        Matcher m = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*(" + NUMBER + ")").matcher(json);
        if (!m.find()) {
            throw new IllegalStateException("Champ « " + key + " » absent de la réponse Python.");
        }
        return Double.parseDouble(m.group(1));
    }

    // Lit une liste de nombres : "clé": [1.0, 2.0]
    public static double[] readDoubleArray(String json, String key) {
        Matcher m = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\\[([^\\]]*)\\]").matcher(json);
        if (!m.find()) {
            throw new IllegalStateException("Champ « " + key + " » absent de la réponse Python.");
        }
        List<Double> values = new ArrayList<>();
        Matcher n = Pattern.compile(NUMBER).matcher(m.group(1));
        while (n.find()) {
            values.add(Double.parseDouble(n.group()));
        }
        return values.stream().mapToDouble(Double::doubleValue).toArray();
    }
}
