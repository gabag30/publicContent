package org.wipo.das.requests;

import okhttp3.*;


import java.io.IOException;

/**
 * Exchanges a client-assertion JWT for an OAuth2 access token.
 *
 * <p>Endpoint: {@code POST {issuer}/access_token}
 * <br>Headers: {@code Content-Type: application/x-www-form-urlencoded}
 * <br>Body params:
 * <ul>
 *   <li>{@code grant_type=client_credentials}</li>
 *   <li>{@code scope} – via method parameter</li>
 *   <li>{@code client_assertion_type=urn:ietf:params:oauth:client-assertion-type:jwt-bearer}</li>
 *   <li>{@code client_assertion} – ES256-signed JWT</li>
 * </ul>
 * Response: JSON string containing {@code access_token}, {@code token_type}, {@code expires_in}, etc.
 */
public class GetToken {

    /**
     * Performs the token request using a client assertion.
     *
     * @param jwtAssertion ES256-signed client assertion JWT.
     * @param issuer OAuth authorization server base URL (e.g. {@code https://.../oauth2}).
     * @param scope Space-delimited scopes requested (e.g. {@code das-api/office-exchange}).
     * @return Raw response body as JSON string containing at least {@code access_token}.
     * @throws IOException if the HTTP call fails or the response cannot be read.
     */
    public static String getAccessToken(String jwtAssertion, String issuer, String scope) throws IOException {
        // Generate access token
        OkHttpClient client = new OkHttpClient().newBuilder().build();

        MediaType mediaType = MediaType.parse("application/x-www-form-urlencoded");
        String requestBodyString = String.format("grant_type=client_credentials&scope=%s&client_assertion_type=urn:ietf:params:oauth:client-assertion-type:jwt-bearer&client_assertion=%s",
                scope, jwtAssertion);
        RequestBody body = RequestBody.create(mediaType, requestBodyString);

        Request request = new Request.Builder()
                .url(issuer + "/access_token")
                .method("POST", body)
                .addHeader("Content-Type", "application/x-www-form-urlencoded")
                .build();

        Response response = client.newCall(request).execute();
        if (response.isSuccessful()) {
            String responseBodyString = response.body().string();
            return responseBodyString;
        } else {
            System.err.println("Failed to get access token");
            System.err.println(response.body().string());
            System.exit(1);
            return null;
        }
    }

}
