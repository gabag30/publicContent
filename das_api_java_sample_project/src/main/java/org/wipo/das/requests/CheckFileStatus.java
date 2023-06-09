package org.wipo.das.requests;

import okhttp3.*;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;
import org.wipo.das.restapitest.ConfigManager;

import java.io.IOException;
import java.util.Properties;

public class CheckFileStatus {

    private static final Logger logger = ConfigManager.getLogger();

    private final String fileId;
    private final String url;
    private final String token;

    public CheckFileStatus(String dasEndPoint, String authorizationToken, String fileId) {
        this.fileId = fileId;
        this.url = dasEndPoint+"/files";
        this.token = authorizationToken;
    }

    public String getFileStatus() throws IOException {
        logger.info("Checking file status...");
        String fileStatus = null;
        // Create a custom client with a longer timeout
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                .build();

         while (fileStatus == null) {
            Request request = new Request.Builder()
                    .url(String.format("%s?fileId=%s", url, fileId))
                    .method("GET", null)
                    //.addHeader("Accept", "application/json")
                    .addHeader("Authorization", "Bearer " + token)
                    .build();
            //logger.warn(request.toString());
            Response response = client.newCall(request).execute();
            String responseBody = response.body().string();

            logger.info(responseBody);

            if (response.isSuccessful()) {
                JSONObject jsonObject = new JSONObject(responseBody);
                String fileSizeQuantity = jsonObject.optString("fileSizeQuantity");
                String fileStatusCategory = jsonObject.optString("fileStatusCategory");
                String error = jsonObject.optString("error");

                if (fileSizeQuantity == null || fileSizeQuantity.equals("")) {
                    logger.warn("File is still being processed.");
                } else if (fileStatusCategory != null) {
                    logger.warn("File was accepted! with SizeQuantity=" + fileSizeQuantity);
                    fileStatus = "ACCEPTED";
                } else if (error != null) {
                    logger.error(String.format("File was rejected due to the following error: %s", error));
                    logger.error(response.toString());
                    fileStatus = "REJECTED";
                } 
            } else {
                logger.error(String.format("Failed to check file status. Response status: %d", response.code()));
                logger.error(String.format("Response body: %s", responseBody));
            }

            // Wait for 5 seconds before checking again
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                logger.error("Thread interrupted while waiting to check file status");
            }
        }

        return fileStatus;
    }
}
