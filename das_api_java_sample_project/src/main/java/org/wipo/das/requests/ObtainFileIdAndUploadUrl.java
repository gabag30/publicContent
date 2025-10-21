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

/**
 * Requests a new {@code fileId} and a pre-signed {@code fileUploadUrl} for uploading a document.
 *
 * <p>Endpoint: {@code POST {das}/files/url-uploads}
 * <br>Headers: {@code Authorization: Bearer <token>}, {@code Content-Type: application/json}, {@code Accept: application/json}
 * <br>Body:
 * <pre>
 * {
 *   "fileReference": "<name>",
 *   "fileFormatCategory": "pdf",
 *   "fileChecksum": "<sha256-hex>"
 * }
 * </pre>
 * Success: returns JSON with {@code fileId} and {@code fileUploadUrl}.
 */
public class ObtainFileIdAndUploadUrl {

    private static final Logger logger = ConfigManager.getLogger();

    private final String fileReference;
    private final String fileFormatCategory;
    private final String fileChecksum;
    private final String url;
    private final String token;

    /**
     * Creates a request helper for the file-id + upload-url transaction.
     *
     * @param dasEndPoint Base DAS requests URL (e.g. {@code .../das-api/v1/requests}).
     * @param authorizationToken OAuth2 bearer token.
     * @param fileReference Client-defined file reference (typically derived from the filename).
     * @param fileFormatCategory File format category (e.g., {@code pdf}).
     * @param fileChecksum SHA-256 checksum (hex) of the file to be uploaded.
     */
    public ObtainFileIdAndUploadUrl(String dasEndPoint, String authorizationToken, String fileReference, String fileFormatCategory, String fileChecksum) {
        this.fileReference = fileReference;
        this.fileFormatCategory = fileFormatCategory;
        this.fileChecksum = fileChecksum;
        this.url = dasEndPoint +"/files/url-uploads";
        this.token = authorizationToken;


    }

    /**
     * Calls DAS to obtain a {@code fileId} and a pre-signed {@code fileUploadUrl}.
     *
     * @return array: index 0 = {@code fileId}, index 1 = {@code fileUploadUrl}; {@code null} if request failed (see logs).
     * @throws IOException when the HTTP request fails or the response cannot be parsed.
     */
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

    /**
     * Computes the SHA-256 checksum of the file at {@code filePath} and returns it in hex encoding.
     *
     * @param filePath path to the file.
     * @return lowercase hex string of the SHA-256 digest.
     * @throws NoSuchAlgorithmException if SHA-256 is unavailable.
     * @throws IOException if the file cannot be read.
     */
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

    /**
     * Derives a file reference from the filename (basename without extension).
     *
     * @param filePath path to the file.
     * @return file reference string.
     */
    public static String getFileReference(String filePath) {
        String[] tokens = filePath.split("/");
        String fileName = tokens[tokens.length - 1];
        String[] nameTokens = fileName.split("\\.");
        return nameTokens[0];
    }
}
