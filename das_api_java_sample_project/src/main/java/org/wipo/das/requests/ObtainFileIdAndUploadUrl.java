package org.wipo.das.requests;

import okhttp3.*;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;
import org.wipo.das.restapitest.ConfigManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Properties;

public class ObtainFileIdAndUploadUrl {

    private static final Logger logger = ConfigManager.getLogger();

    private final String fileReference;
    private final String fileFormatCategory;
    private final String fileChecksum;
    private final String url;
    private final String token;

    public ObtainFileIdAndUploadUrl(String dasEndPoint, String authorizationToken, String fileReference, String fileFormatCategory, String fileChecksum) {
        this.fileReference = fileReference;
        this.fileFormatCategory = fileFormatCategory;
        this.fileChecksum = fileChecksum;
        this.url = dasEndPoint +"/files/url-uploads";
        this.token = authorizationToken;


    }

    public String[] getFileIdAndUploadUrl() throws IOException {
        logger.info("Obtaining file ID and upload URL...");
        
        // Create a custom client with a longer timeout
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                .build();

        String json = new JSONObject()
                .put("fileReference", fileReference)
                .put("fileFormatCategory", fileFormatCategory)
                .put("fileChecksum", fileChecksum)
                .toString();

        RequestBody body = RequestBody.create(json, MediaType.parse("application/json"));

        Request request = new Request.Builder()
                .url(url)
                .method("POST", body)
                .addHeader("Content-Type", "application/json")
                .addHeader("Accept", "application/json")
                .addHeader("Authorization", "Bearer " + token)
                .build();

        Response response = client.newCall(request).execute();
        String responseBody = response.body().string();

        //logger.info(responseBody);

        if (response.isSuccessful()) {
            JSONObject jsonObject = new JSONObject(responseBody);
            String fileId = jsonObject.getString("fileId");
            String uploadUrl = jsonObject.getString("fileUploadUrl");
            //logger.info(String.format("File ID: %s", fileId));
            //logger.info(String.format("Upload URL: %s", uploadUrl));
            return new String[]{fileId, uploadUrl};
        } else {
            logger.error(String.format("Failed to obtain file ID and upload URL. Response status: %d", response.code()));
            logger.error(String.format("Response body: %s", responseBody));
            return null;
        }
    }

    public static String getFileChecksum(String filePath) throws NoSuchAlgorithmException, IOException {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        md.update(Files.readAllBytes(Paths.get(filePath)));
        byte[] digest = md.digest();
        StringBuilder sb = new StringBuilder();
        for (byte b : digest) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    public static String getFileReference(String filePath) {
        String[] tokens = filePath.split("/");
        String fileName = tokens[tokens.length - 1];
        String[] nameTokens = fileName.split("\\.");
        return nameTokens[0];
    }
}
