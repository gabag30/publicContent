package org.wipo.das.assertion;


import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.security.PrivateKey;
import java.security.Security;
import java.time.Instant;
import java.util.Properties;
import java.util.Date;
import java.security.interfaces.ECPrivateKey;

import java.io.File;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;

import org.bouncycastle.openssl.PEMKeyPair;

import org.wipo.das.restapitest.ConfigManager;



/**
 * Builds an ES256-signed client assertion JWT used to obtain an OAuth2 access token.
 *
 * <p>The private key is loaded from the PEM file configured via {@code config.properties} (key: {@code pemFile}).
 * Claims produced:
 * <ul>
 *   <li>{@code iss} = {@code clientId}</li>
 *   <li>{@code sub} = {@code clientId}</li>
 *   <li>{@code aud} = {@code audience}</li>
 *   <li>{@code exp} = now + ~1000 seconds</li>
 * </ul>
 */
public class JwtAssertionGenerator {

    private final ConfigManager config;

    /**
     * @param configManager Provides access to {@code config.properties} holding the PEM path and OAuth metadata.
     */
    public JwtAssertionGenerator(ConfigManager configManager) {
        this.config = configManager;
    }

    /**
     * Generates an ES256-signed JWT client assertion using the configured EC private key.
     *
     * @return serialized compact JWT string suitable for {@code client_assertion}.
     * @throws IOException when reading the PEM file fails.
     * @throws JOSEException when signing fails.
     * @throws NoSuchAlgorithmException if the EC algorithm is unavailable.
     * @throws InvalidKeySpecException if the PEM key cannot be parsed as PKCS#8/EC.
     */
    public String generateAssertion() throws IOException, JOSEException, NoSuchAlgorithmException, InvalidKeySpecException {
        String pemFile = config.getConfig().getProperty("pemFile");
        String issuer = config.getConfig().getProperty("issuer");
        String clientId = config.getConfig().getProperty("clientId");
        String audience = config.getConfig().getProperty("audience");

        PrivateKey privateKey = loadPrivateKey(new File(pemFile));

        if (!(privateKey instanceof ECPrivateKey)) {
    throw new IllegalArgumentException("The provided private key must be an instance of ECPrivateKey");
        }
        ECPrivateKey ecPrivateKey = (ECPrivateKey) privateKey;
        ECDSASigner signer = new ECDSASigner(ecPrivateKey);


        Instant currentTime = Instant.now();
       
        Instant expirationTime = currentTime.plusSeconds(1000);

        JWTClaimsSet claimsSet = new JWTClaimsSet.Builder()
                .issuer(clientId)
                .subject(clientId)
                .audience(audience)
                .expirationTime(Date.from(expirationTime))
                .build();

        SignedJWT signedJWT = new SignedJWT(new JWSHeader(JWSAlgorithm.ES256), claimsSet);
        signedJWT.sign(signer);

        return signedJWT.serialize();
    }

    /**
     * Loads an EC private key from a PEM file. Supports PKCS#8 or key-pair entries.
     *
     * @param pemFile location of the private key in PEM format.
     * @return a {@link PrivateKey} instance (EC private key expected).
     * @throws IOException on file access or parsing errors.
     * @throws NoSuchAlgorithmException if EC algorithm is unavailable.
     * @throws InvalidKeySpecException if the PEM content cannot be converted to a private key.
     */
    private static PrivateKey loadPrivateKey(File pemFile) throws IOException, NoSuchAlgorithmException, InvalidKeySpecException {
    try (FileReader reader = new FileReader(pemFile);
         PEMParser pemParser = new PEMParser(reader)) {

        Object pemObject = pemParser.readObject();
        PrivateKeyInfo privateKeyInfo;

        if (pemObject instanceof PEMKeyPair) {
            PEMKeyPair pemKeyPair = (PEMKeyPair) pemObject;
            privateKeyInfo = pemKeyPair.getPrivateKeyInfo();
        } else if (pemObject instanceof PrivateKeyInfo) {
            privateKeyInfo = (PrivateKeyInfo) pemObject;
        } else {
            throw new IllegalArgumentException("Invalid PEM object: " + pemObject.getClass());
        }

        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(privateKeyInfo.getEncoded());
        KeyFactory keyFactory = KeyFactory.getInstance("EC");
        return keyFactory.generatePrivate(keySpec);
    }
}

}
