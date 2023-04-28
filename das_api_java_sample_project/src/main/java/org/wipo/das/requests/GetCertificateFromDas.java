package org.wipo.das.requests;

import java.io.IOException;
import java.util.Properties;

import okhttp3.*;

import org.apache.logging.log4j.Logger;
import org.json.JSONObject;
import org.wipo.das.restapitest.ConfigManager;
import java.nio.file.Files;
import java.nio.file.Paths;



public class GetCertificateFromDas {

    private static final Logger logger = ConfigManager.getLogger();

    private final String url;
    private final String token;
    private final String documentKindCategory;
    private final String documentNumber;
    private final String documentDate;
    private final String dasAccessCode;
    private final String outputFolderPath;
    private final String outputFileName;


    public GetCertificateFromDas(String dasEndpoint, String authorizationToken, String documentKindCategory,
            String documentNumber, String documentDate, String dasAccessCode,
            String outputFolderPath, String outputFileName) {
        this.url = dasEndpoint + "/registrations/certificates";
        this.token = authorizationToken;
        this.documentKindCategory = documentKindCategory;
        this.documentNumber = documentNumber;
        this.documentDate = documentDate;
        this.dasAccessCode = dasAccessCode;
        this.outputFolderPath = outputFolderPath;
        this.outputFileName = outputFileName;
        }

    public boolean getCertificate() throws IOException {
        logger.info("Downloading certificate...");
        //System.out.println(token);

        OkHttpClient client = new OkHttpClient.Builder()
        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .build();

        HttpUrl.Builder urlBuilder = HttpUrl.parse(url).newBuilder()
                .addQueryParameter("documentKindCategory", documentKindCategory)
                .addQueryParameter("documentNumber", documentNumber)
                .addQueryParameter("documentDate", documentDate)
                .addQueryParameter("dasAccessCode", dasAccessCode);
        //System.out.println(urlBuilder.toString());

        Request request = new Request.Builder()
                .url(urlBuilder.build())
                .method("GET", null)
                .addHeader("Authorization", "Bearer " + token)
                .addHeader("Content-Type", "application/pdf")
                .build();
        //System.out.println(request.toString());
        Response response = client.newCall(request).execute();

        if (response.isSuccessful()) {
            Files.createDirectories(Paths.get(outputFolderPath));
            Files.write(Paths.get(outputFolderPath, outputFileName), response.body().bytes());
            logger.info("Certificate downloaded successfully.");
            return true;
        } else {
            logger.error(String.format("Failed to download certificate. Response status: %d", response.code()));
            //logger.error(String.format("Failed to download certificate. Response status: %d. Response body: %s", response.code(), response.body().string()));

            return false;
        }
    }
}
