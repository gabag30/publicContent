package org.wipo.das.requests;

import okhttp3.*;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Properties;

/**
 * Requests a refreshed pre-signed upload URL for an existing {@code fileId}.
 *
 * <p>Endpoint: {@code PUT {das}/files/url-uploads}
 * <br>Headers: {@code Authorization: Bearer <token>}, {@code Content-Type: application/json}
 * <br>Body:
 * <pre>
 * { "fileId": "<fileId>" }
 * </pre>
 * Success: returns JSON with a new {@code fileUploadUrl}.
 */
public class GetUpdatedUploadUrl {

    private final String url;
    private final String token;

    /**
     * @param baseUrl Base DAS requests URL (should resolve to {@code .../das-api/v1/requests/}).
     * @param authorizationToken OAuth2 bearer token.
     */
    public GetUpdatedUploadUrl(String baseUrl, String authorizationToken) {
        this.url = baseUrl+"files/url-uploads";
        this.token = authorizationToken;
    }

    /**
     * Obtains an updated pre-signed URL for the provided file id.
     *
     * @param fileId The file identifier originally returned by {@code /files/url-uploads}.
     * @return The updated {@code fileUploadUrl} string.
     * @throws IOException if the request fails or the response is not successful.
     */
    public String getUpdatedUrl(String fileId) throws IOException {
        // Create a custom client with a longer timeout
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                .build();

        String json = new JSONObject()
                .put("fileId", fileId)
                .toString();

        RequestBody body = RequestBody.create(json, MediaType.parse("application/json"));

        Request request = new Request.Builder()
                .url(url)
                .method("PUT", body)
                .addHeader("Content-Type", "application/json")
                .addHeader("Accept", "application/json")
                .addHeader("Authorization", "Bearer " + token)
                .build();

        Response response = client.newCall(request).execute();
        String responseBody = response.body().string();

        if (response.isSuccessful()) {
            JSONObject jsonObject = new JSONObject(responseBody);
            String updatedUrl = jsonObject.getString("fileUploadUrl");
            return updatedUrl;
        } else {
            throw new IOException(String.format("Failed to obtain updated URL. Response status: %d, Response body: %s", response.code(), responseBody));
        }
    }
}
