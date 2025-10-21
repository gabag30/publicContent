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

import java.io.File;
import java.io.IOException;

/**
 * Uploads a PDF file to a pre-signed upload URL returned by DAS.
 *
 * <p>Endpoint: {@code PUT {fileUploadUrl}} (pre-signed)
 * <br>Headers: {@code Content-Type: application/pdf}
 * <br>Body: raw PDF bytes
 * <br>Auth: Not required; the URL is pre-signed.
 */
public class UploadFileToDas {

    private static final Logger logger = ConfigManager.getLogger();

    private static String uploadUrl;
    private static String filePath;

    /**
     * Creates an uploader bound to a specific pre-signed URL and local file path.
     *
     * @param uploadUrl pre-signed URL from DAS for file upload.
     * @param filePath local path to the PDF file to upload.
     */
    public UploadFileToDas(String uploadUrl, String filePath)  {
        this.uploadUrl = uploadUrl;
        this.filePath = filePath;
    }

    /**
     * Executes the HTTP PUT to upload the file.
     *
     * @return HTTP status code from the upload request (200 indicates success).
     * @throws IOException if the upload fails or the file cannot be read.
     */
    public static Integer uploadMyFile() throws IOException {

        logger.info("uploading file...");
        OkHttpClient client = new OkHttpClient().newBuilder()
                .build();

        MediaType mediaType = MediaType.parse("application/pdf");
        RequestBody body = RequestBody.create(mediaType, new File(filePath));
        Request request = new Request.Builder()
                .url(uploadUrl)
                .method("PUT", body)
                .addHeader("Content-Type", "application/pdf")
                .build();

        Response response = client.newCall(request).execute();


        if (response.isSuccessful()) {
            return response.code();
        } else {
            logger.error("Failed to upload file. Response code: " + response.code());
            return response.code();
        }

    }
}
