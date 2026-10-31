import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/*
 * Récupère les prix journaliers sur l'API Twelve Data (endpoint time_series).
 *
 * La clé API est lue dans la variable d'environnement TWELVE_DATA_API_KEY :
 * elle ne doit JAMAIS être écrite dans le code (le dépôt est public).
 */
public class TwelveDataProvider implements PriceProvider {

    private static final String BASE_URL = "https://api.twelvedata.com/time_series";

    // Offre gratuite : 8 requêtes par minute, donc au moins 8 s entre deux appels.
    private static final long MIN_DELAY_MILLIS = 8_000;

    // Twelve Data limite outputsize à 5000 points par requête.
    private static final int MAX_POINTS = 5000;

    // Dans la réponse JSON, chaque jour ressemble à :
    // {"datetime":"2026-10-29","open":"...","high":"...","low":"...","close":"229.50","volume":"..."}
    private static final Pattern DAY = Pattern.compile(
            "\"datetime\"\\s*:\\s*\"(\\d{4}-\\d{2}-\\d{2})\"[^}]*?\"close\"\\s*:\\s*\"([0-9.]+)\"");
    private static final Pattern ERROR_MESSAGE = Pattern.compile("\"message\"\\s*:\\s*\"([^\"]*)\"");

    private final String apiKey;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private long lastCallMillis = 0;

    // Lit la clé dans l'environnement.
    public TwelveDataProvider() {
        this(System.getenv("TWELVE_DATA_API_KEY"));
    }

    public TwelveDataProvider(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "Clé Twelve Data absente : définis la variable d'environnement TWELVE_DATA_API_KEY.");
        }
        this.apiKey = apiKey.trim();
    }

    @Override
    public TreeMap<LocalDate, Double> fetchDailyCloses(String symbol, int days) {
        if (symbol == null || symbol.isBlank()) {
            throw new IllegalArgumentException("Le symbole est obligatoire.");
        }
        if (days <= 0 || days > MAX_POINTS) {
            throw new IllegalArgumentException("Le nombre de jours doit être entre 1 et " + MAX_POINTS + ".");
        }

        String url = BASE_URL
                + "?symbol=" + URLEncoder.encode(symbol.trim().toUpperCase(), StandardCharsets.UTF_8)
                + "&interval=1day"
                + "&outputsize=" + days
                + "&apikey=" + URLEncoder.encode(apiKey, StandardCharsets.UTF_8);

        String body = get(url);

        // En cas d'erreur, Twelve Data répond {"code":..., "message":"...", "status":"error"}.
        if (body.contains("\"status\":\"error\"")) {
            Matcher m = ERROR_MESSAGE.matcher(body);
            String message = m.find() ? m.group(1) : body;
            throw new IllegalStateException("Twelve Data (" + symbol + ") : " + message);
        }

        TreeMap<LocalDate, Double> closes = new TreeMap<>();
        Matcher m = DAY.matcher(body);
        while (m.find()) {
            closes.put(LocalDate.parse(m.group(1)), Double.parseDouble(m.group(2)));
        }
        if (closes.isEmpty()) {
            throw new IllegalStateException("Aucun prix reçu de Twelve Data pour " + symbol + ".");
        }
        return closes;
    }

    private String get(String url) {
        waitForRateLimit();
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IllegalStateException("Twelve Data a répondu " + response.statusCode() + ".");
            }
            return response.body();
        } catch (IOException e) {
            throw new IllegalStateException("Impossible de joindre Twelve Data.", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Appel à Twelve Data interrompu.", e);
        }
    }

    // Attend si le dernier appel est trop récent, pour rester dans la limite gratuite.
    private void waitForRateLimit() {
        long wait = lastCallMillis + MIN_DELAY_MILLIS - System.currentTimeMillis();
        if (wait > 0) {
            try {
                Thread.sleep(wait);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        lastCallMillis = System.currentTimeMillis();
    }
}
