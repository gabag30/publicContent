package org.wipo.das.requests;

import java.io.IOException;
import java.util.Properties;

import okhttp3.*;

import org.apache.logging.log4j.Logger;
import org.json.JSONObject;
import org.wipo.das.restapitest.ConfigManager;

public class GetOsfAckId {

    private static final Logger logger = ConfigManager.getLogger();

    private final String url;
    private final String token;
    private final String documentCategory;
    private final String documentNumber;
    private final String documentDate;
    private final String dasAccessCode;


    public GetOsfAckId(String dasEndpoint, String authorizationToken, String documentCategory,
            String documentNumber, String documentDate, String dasAccessCode
            ) {
        this.url = dasEndpoint + "/retrievals";
        this.token = authorizationToken;
        this.documentCategory = documentCategory;
        this.documentNumber = documentNumber;
        this.documentDate = documentDate;
        this.dasAccessCode = dasAccessCode;
        }

    public String getAck() throws IOException {
        logger.info("Registering retrieval request...");

        // Create a custom client with a longer timeout
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                .build();

        MediaType mediaType = MediaType.parse("application/json");
        String requestBody = String.format("{\n  \"operationCategory\": \"retrieval\",\n  \"documentKindCategory\": \"%s\",\n  \"documentNumber\": \"%s\",\n  \"documentDate\": \"%s\",\n  \"dasAccessCode\": \"%s\",\n  \"applicationCategory\": null,\n  \"applicationNumber\": null,\n  \"applicationFilingDate\": null\n}\n\n", documentCategory, documentNumber, documentDate, dasAccessCode);
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
            String requestAckId = jsonObject.optString("requestAckId");
            return requestAckId;
        } else {
            logger.error(String.format("Failed to register request. Response status: %d", response.code()));
            logger.error(String.format("Response body: %s", responseBody));
            return null;
        }
    }
}
