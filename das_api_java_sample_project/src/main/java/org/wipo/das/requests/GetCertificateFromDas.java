package org.wipo.das.requests;

import java.io.IOException;
import java.util.Properties;

import okhttp3.*;

import org.apache.logging.log4j.Logger;
import org.json.JSONObject;
import org.wipo.das.restapitest.ConfigManager;
import java.nio.file.Files;
import java.nio.file.Paths;



/**
 * Downloads a registration certificate PDF for a previously registered document.
 *
 * <p>Endpoint: {@code GET {das}/registrations/certificates}
 * <br>Headers: {@code Authorization: Bearer <token>}, {@code Content-Type: application/pdf}
 * <br>Query parameters: {@code documentKindCategory}, {@code documentNumber}, {@code documentDate}, {@code dasAccessCode}
 * <br>Behavior: writes the response bytes to {@code outputFolderPath/outputFileName}.
 */
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


    /**
     * @param dasEndpoint Base DAS requests URL (e.g. {@code .../das-api/v1/requests}).
     * @param authorizationToken OAuth2 bearer token.
     * @param documentKindCategory Document kind category (e.g., {@code patent}).
     * @param documentNumber Priority or document number.
     * @param documentDate Document date in ISO-8601 (YYYY-MM-DD).
     * @param dasAccessCode DAS access code linked to the registration.
     * @param outputFolderPath Local folder to create if absent and where to save the PDF.
     * @param outputFileName Target PDF filename.
     */
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

    /**
     * Performs the certificate download request and writes the PDF to disk.
     *
     * @return {@code true} if the file was saved successfully; {@code false} otherwise.
     * @throws IOException if networking fails or the output cannot be written.
     */
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
