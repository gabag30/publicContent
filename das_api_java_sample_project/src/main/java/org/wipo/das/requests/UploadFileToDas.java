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

public class UploadFileToDas {

    private static final Logger logger = ConfigManager.getLogger();

    private static String uploadUrl;
    private static String filePath;

    public UploadFileToDas(String uploadUrl, String filePath)  {
        this.uploadUrl = uploadUrl;
        this.filePath = filePath;
    }

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
