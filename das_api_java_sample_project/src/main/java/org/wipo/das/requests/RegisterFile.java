package org.wipo.das.requests;

import java.io.IOException;
import java.util.Properties;

import okhttp3.*;

import org.apache.logging.log4j.Logger;
import org.json.JSONObject;
import org.wipo.das.restapitest.ConfigManager;

public class RegisterFile {

    private static final Logger logger = ConfigManager.getLogger();

    private final String url;
    private final String token;
    private final String documentCategory;
    private final String documentNumber;
    private final String documentDate;
    private final String dasAccessCode;
    private final String applicationCategory;
    private final String applicationNumber;
    private final String applicationFilingDate;
    private final String fileId;

    public RegisterFile(String dasEndpoint, String authorizationToken, String documentCategory,
            String documentNumber, String documentDate, String dasAccessCode,
            String applicationCategory, String applicationNumber, String applicationFilingDate, String fileId) {
        this.url = dasEndpoint + "/registrations";
        this.token = authorizationToken;
        this.documentCategory = documentCategory;
        this.documentNumber = documentNumber;
        this.documentDate = documentDate;
        this.dasAccessCode = dasAccessCode;
        this.applicationCategory = applicationCategory;
        this.applicationNumber = applicationNumber;
        this.applicationFilingDate = applicationFilingDate;
        this.fileId=fileId;
    }

    public String registerFile() throws IOException {
        logger.info("Registering file...");

        // Create a custom client with a longer timeout
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                .build();

        MediaType mediaType = MediaType.parse("application/json");
        String requestBody = String.format("{\n  \"operationCategory\": \"registration\",\n  \"documentKindCategory\": \"%s\",\n  \"documentNumber\": \"%s\",\n  \"documentDate\": \"%s\",\n  \"dasAccessCode\": \"%s\",\n  \"applicationCategory\": \"%s\",\n  \"applicationNumber\": \"%s\",\n  \"applicationFilingDate\": \"%s\",\n  \"email\": null,\n  \"fileId\": \"%s\"\n}\n\n", documentCategory, documentNumber, documentDate, dasAccessCode, applicationCategory, applicationNumber, applicationFilingDate,fileId);
        RequestBody body = RequestBody.create(mediaType, requestBody);
        Request request = new Request.Builder()
                .url(url)
                .method("POST", body)
                .addHeader("Authorization", "Bearer " + token)
                .addHeader("Content-Type", "application/json")
                .build();

        Response response = client.newCall(request).execute();
        String responseBody = response.body().string();
        logger.info(responseBody);

        if (response.isSuccessful()) {
            JSONObject jsonObject = new JSONObject(responseBody);
            String requestAckId = jsonObject.optString("requestAckId");
            return requestAckId;
        } else {
            logger.error(String.format("Failed to register file. Response status: %d", response.code()));
            logger.error(String.format("Response body: %s", responseBody));
            return null;
        }
    }
}
