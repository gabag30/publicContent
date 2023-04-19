package org.wipo.das.requests;

import okhttp3.*;


import java.io.IOException;

public class GetToken {

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
