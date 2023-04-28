package org.wipo.das.requests;

import java.io.IOException;
import java.util.Properties;

import okhttp3.*;

import org.apache.logging.log4j.Logger;
import org.json.JSONObject;
import org.wipo.das.restapitest.ConfigManager;

public class GetFileFromDas {

    private static final Logger logger = ConfigManager.getLogger();

    private final String url;
    private final String token;
    private final String documentKindCategory;
    private final String documentNumber;
    private final String documentDate;
    private final String osfAckId;


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
