package com.finquity.ui;

import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.finquity.api.Dto.AddUserRequest;
import com.finquity.api.Dto.AssetRequest;
import com.finquity.api.Dto.ChangePasswordRequest;
import com.finquity.api.Dto.LoginRequest;
import com.finquity.api.Dto.PortfolioView;
import com.finquity.api.Dto.RegisterRequest;
import com.finquity.api.Dto.StatementRequest;
import com.finquity.api.Dto.TradeRequest;
import com.finquity.api.Dto.UserView;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.CookieManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/*
 * La SEULE classe de l'interface qui parle au serveur Spring Boot.
 * Les écrans appellent ses méthodes et ne font jamais de HTTP eux-mêmes.
 *
 * Le CookieManager garde le cookie de session (JSESSIONID) entre deux requêtes :
 * une fois connecté, le serveur sait qui on est.
 */
public class ApiClient {

    private final String baseUrl;
    private final HttpClient http;
    private final ObjectMapper json;

    public ApiClient(String baseUrl) {
        this.baseUrl = baseUrl;
        this.http = HttpClient.newBuilder().cookieHandler(new CookieManager()).build();
        this.json = JsonMapper.builder()
                .enable(JsonReadFeature.ALLOW_NON_NUMERIC_NUMBERS) // le serveur peut renvoyer NaN
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build();
    }

    // ---------- Comptes ----------

    public UserView login(String email, String password) throws ApiException {
        return post("/api/auth/login", new LoginRequest(email, password), UserView.class);
    }

    public UserView register(RegisterRequest request) throws ApiException {
        return post("/api/auth/register", request, UserView.class);
    }

    public void logout() throws ApiException {
        send(HttpRequest.newBuilder(uri("/api/auth/logout")).POST(HttpRequest.BodyPublishers.noBody()));
    }

    public UserView me() throws ApiException {
        return read(get("/api/auth/me"), UserView.class);
    }

    public UserView addColleague(AddUserRequest request) throws ApiException {
        return post("/api/auth/users", request, UserView.class);
    }

    public void changePassword(String oldPassword, String newPassword) throws ApiException {
        postJson("/api/auth/password", new ChangePasswordRequest(oldPassword, newPassword));
    }

    // ---------- Entreprise ----------

    public JsonNode getCompany() throws ApiException {
        return tree(get("/api/company"));
    }

    public JsonNode getUsers() throws ApiException {
        return tree(get("/api/company/users"));
    }

    public JsonNode getStatements() throws ApiException {
        return tree(get("/api/company/statements"));
    }

    public JsonNode saveStatement(StatementRequest request) throws ApiException {
        return tree(postJson("/api/company/statements", request));
    }

    public JsonNode getDiagnosis() throws ApiException {
        return tree(get("/api/company/diagnosis"));
    }

    // Envoie un fichier Excel au serveur (formulaire "multipart", champ "file").
    public JsonNode importExcel(Path file) throws ApiException {
        String boundary = "----finquity" + UUID.randomUUID();
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        try {
            String head = "--" + boundary + "\r\n"
                    + "Content-Disposition: form-data; name=\"file\"; filename=\"" + file.getFileName() + "\"\r\n"
                    + "Content-Type: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet\r\n\r\n";
            body.write(head.getBytes(StandardCharsets.UTF_8));
            body.write(Files.readAllBytes(file));
            body.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new ApiException("Impossible de lire le fichier " + file.getFileName() + ".");
        }
        return tree(send(HttpRequest.newBuilder(uri("/api/company/import"))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()))));
    }

    // Télécharge le rapport PDF et l'enregistre à l'endroit choisi.
    public void downloadReport(Path target) throws ApiException {
        HttpResponse<byte[]> res = sendBytes(HttpRequest.newBuilder(uri("/api/company/report")).GET());
        try {
            Files.write(target, res.body());
        } catch (IOException e) {
            throw new ApiException("Impossible d'enregistrer le fichier.");
        }
    }

    // ---------- Placements ----------

    public JsonNode getAssets() throws ApiException {
        return tree(get("/api/assets"));
    }

    public JsonNode addAsset(AssetRequest request) throws ApiException {
        return tree(postJson("/api/assets", request));
    }

    public PortfolioView getPortfolio() throws ApiException {
        return read(get("/api/portfolio"), PortfolioView.class);
    }

    public PortfolioView buy(String symbol, int quantity) throws ApiException {
        return post("/api/portfolio/buy", new TradeRequest(symbol, quantity), PortfolioView.class);
    }

    public PortfolioView sell(String symbol, int quantity) throws ApiException {
        return post("/api/portfolio/sell", new TradeRequest(symbol, quantity), PortfolioView.class);
    }

    // Met à jour l'historique des prix de tous les placements (Twelve Data, clé TWELVE_DATA_API_KEY côté serveur).
    public JsonNode updateAllPrices() throws ApiException {
        return tree(send(HttpRequest.newBuilder(uri("/api/assets/prices/update-all?days=750"))
                .POST(HttpRequest.BodyPublishers.noBody())));
    }

    public JsonNode simulate(int years, int simulations) throws ApiException {
        return tree(get("/api/portfolio/simulation?years=" + years + "&simulations=" + simulations));
    }

    // ---------- Outils communs ----------

    private String get(String path) throws ApiException {
        return send(HttpRequest.newBuilder(uri(path)).GET());
    }

    private String postJson(String path, Object body) throws ApiException {
        try {
            return send(HttpRequest.newBuilder(uri(path))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))));
        } catch (IOException e) {
            throw new ApiException("Données impossibles à envoyer.");
        }
    }

    private <T> T post(String path, Object body, Class<T> type) throws ApiException {
        return read(postJson(path, body), type);
    }

    // Envoie la requête ; si le serveur répond une erreur, on lève une ApiException avec son message.
    private String send(HttpRequest.Builder request) throws ApiException {
        HttpResponse<byte[]> res = sendBytes(request);
        return new String(res.body(), StandardCharsets.UTF_8);
    }

    private HttpResponse<byte[]> sendBytes(HttpRequest.Builder request) throws ApiException {
        HttpResponse<byte[]> res;
        try {
            res = http.send(request.build(), HttpResponse.BodyHandlers.ofByteArray());
        } catch (IOException e) {
            throw new ApiException("Serveur injoignable. Lancez d'abord « mvn spring-boot:run ».");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiException("Requête interrompue.");
        }
        if (res.statusCode() >= 400) {
            throw new ApiException(errorMessage(new String(res.body(), StandardCharsets.UTF_8), res.statusCode()));
        }
        return res;
    }

    private String errorMessage(String body, int status) {
        try {
            JsonNode node = json.readTree(body);
            if (node != null && node.hasNonNull("error")) {
                return node.get("error").asText();
            }
        } catch (IOException ignored) {
            // corps vide ou pas en JSON
        }
        return "Erreur du serveur (" + status + ").";
    }

    private JsonNode tree(String body) throws ApiException {
        try {
            return body.isEmpty() ? json.nullNode() : json.readTree(body);
        } catch (IOException e) {
            throw new ApiException("Réponse du serveur illisible.");
        }
    }

    private <T> T read(String body, Class<T> type) throws ApiException {
        try {
            return json.readValue(body, type);
        } catch (IOException e) {
            throw new ApiException("Réponse du serveur illisible.");
        }
    }

    private URI uri(String path) {
        return URI.create(baseUrl + path);
    }
}
