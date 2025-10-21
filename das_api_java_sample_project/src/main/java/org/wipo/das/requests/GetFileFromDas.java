package org.wipo.das.requests;

import java.io.IOException;
import java.util.Properties;

import okhttp3.*;

import org.apache.logging.log4j.Logger;
import org.json.JSONObject;
import org.wipo.das.restapitest.ConfigManager;

/**
 * Obtains a pre-signed download URL for a document previously registered for retrieval.
 *
 * <p>Endpoint: {@code POST {das}/files/url-downloads}
 * <br>Headers: {@code Authorization: Bearer <token>}, {@code Content-Type: application/json}
 * <br>Body:
 * <pre>
 * {
 *   "documentKindCategory": "<document_category>",
 *   "documentNumber": "<priority_number>",
 *   "documentDate": "<priority_date>",
 *   "osfAckId": "<requestAckId>"
 * }
 * </pre>
 * Success: JSON with {@code fileDownloadUrl}.
 */
public class GetFileFromDas {

    private static final Logger logger = ConfigManager.getLogger();

    private final String url;
    private final String token;
    private final String documentKindCategory;
    private final String documentNumber;
    private final String documentDate;
    private final String osfAckId;


    /**
     * @param dasEndpoint Base DAS requests URL (e.g. {@code .../das-api/v1/requests}).
     * @param authorizationToken OAuth2 bearer token.
     * @param documentKindCategory Document kind category (e.g., {@code patent}).
     * @param documentNumber Priority or document number.
     * @param documentDate Document date in ISO-8601 (YYYY-MM-DD).
     * @param osfAckId Acknowledgment id returned by {@code POST /retrievals}.
     */
    public GetFileFromDas(String dasEndpoint, String authorizationToken, String documentKindCategory,
            String documentNumber, String documentDate, String osfAckId
            ) {
        this.url = dasEndpoint + "/files/url-downloads";
        this.token = authorizationToken;
        this.documentKindCategory = documentKindCategory;
        this.documentNumber = documentNumber;
        this.documentDate = documentDate;
        this.osfAckId = osfAckId;
        }

    /**
     * Requests a pre-signed download URL for the document.
     *
     * @return {@code fileDownloadUrl} string on success; {@code null} otherwise (see logs).
     * @throws IOException if the HTTP request fails.
     */
    public String getUrl() throws IOException {
        logger.info("Registering retrieval request...");

        // Create a custom client with a longer timeout
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                .build();

        MediaType mediaType = MediaType.parse("application/json");
        String requestBody = String.format("{\n   \"documentKindCategory\": \"%s\",\n  \"documentNumber\": \"%s\",\n  \"documentDate\": \"%s\",\n  \"osfAckId\": \"%s\"\n}\n\n", documentKindCategory, documentNumber, documentDate, osfAckId);
        RequestBody body = RequestBody.create(mediaType, requestBody);
        Request request = new Request.Builder()
                .url(url)
                .method("POST", body)
                .addHeader("Authorization", "Bearer " + token)
                .addHeader("Content-Type", "application/json")
                .build();

        Response response = client.newCall(request).execute();
        String responseBody = response.body().string();
        //logger.info(responseBody);

        if (response.isSuccessful()) {
            JSONObject jsonObject = new JSONObject(responseBody);
            String downloadUrl = jsonObject.optString("fileDownloadUrl");
            return downloadUrl;
        } else {
            logger.error(String.format("Failed to register request. Response status: %d", response.code()));
            logger.error(String.format("Response body: %s", responseBody));
            return null;
        }
    }
}
