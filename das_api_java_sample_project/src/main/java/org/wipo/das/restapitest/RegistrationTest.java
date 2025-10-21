package org.wipo.das.restapitest;

import com.opencsv.CSVReader;
import com.opencsv.CSVWriter;
import com.opencsv.exceptions.CsvException;
import org.apache.commons.cli.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.wipo.das.requests.*;
import org.wipo.das.assertion.JwtAssertionGenerator;
import org.wipo.das.restapitest.ConfigManager;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Properties;
import java.util.Arrays;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import java.security.NoSuchAlgorithmException;

/**
 * Demonstrates the end-to-end Registration flow:
 * <ol>
 *   <li>OAuth token via client assertion</li>
 *   <li>Obtain {@code fileId} and pre-signed upload URL</li>
 *   <li>Upload the PDF</li>
 *   <li>Poll file processing status</li>
 *   <li>Register the document and capture {@code requestAckId}</li>
 *   <li>Update CSV with results</li>
 * </ol>
 */
public class RegistrationTest {

    private static final Logger logger = ConfigManager.getLogger();
    private static ConfigManager myConfigManager;

    /**
     * Entry point for the Registration flow.
     *
     * @param args {@code [0]} path to {@code config.properties}, {@code [1]} path to {@code registration_test.csv}
     */
    public static void main(String[] args) throws IOException, CsvException, NoSuchAlgorithmException, InterruptedException, Exception {
        if (args.length < 2) {
            System.err.println("Usage: java Main <config_file_path> <csv_file_path>");
            System.exit(1);
        }

        String configFilePath = args[0];
        String csvFilePath = args[1];

        myConfigManager = new ConfigManager(configFilePath, csvFilePath);

        // Retrieve authorization token
        logger.info("Going to retrieve the access token from the oauth server");
        String authToken = getAuthorizationToken();

        if (authToken == null) {
            logger.error("Failed to retrieve authorization token.");
            return;
        }
        logger.info("Authorization token retrieved successfully.");

        String[][] csvData = myConfigManager.getCsvData();

        int rowCounter = 1; // Initialize counter variable to 1
        // Process each row in the CSV data
        for (String[] nextLine : Arrays.copyOfRange(csvData, 1, csvData.length) ){

            String fileReference = nextLine[0];
            String fileLocation = nextLine[1];
            String applicationNumber = nextLine[2];
            String applicationDate = nextLine[3];
            String priorityNumber = nextLine[4];
            String priorityDate = nextLine[5];
            String documentCategory = nextLine[6];
            String applicationCategory = nextLine[7];
            String dasCode = nextLine[8];
            String fileId = nextLine[9];
            String registered = nextLine[10];
            String ackId = nextLine[11];

            // Skip already registered files
            if (registered.equalsIgnoreCase("true")) {
                logger.info(String.format("File '%s' already registered.", fileReference));
                continue;
            }

            // Calculate the SHA-256 checksum of the file
            logger.info("Processing fileReference: " + fileReference);
            String sha256Checksum = calculateSha256(fileLocation);
            logger.info("SHA-256 checksum: " + sha256Checksum);

            // Retrieve the fileId and uploadUrl
            String dasEndPoint = myConfigManager.getConfig().getProperty("url");

            ObtainFileIdAndUploadUrl obtainFileIdAndUploadUrl = new ObtainFileIdAndUploadUrl(dasEndPoint, authToken, fileReference, "pdf", sha256Checksum);
            String[] fileIdAndUrl = obtainFileIdAndUploadUrl.getFileIdAndUploadUrl();

            if (fileIdAndUrl != null) {

                fileId = fileIdAndUrl[0];
                String uploadUrl = fileIdAndUrl[1];

                logger.warn("Got fileId: " + fileId);
                logger.warn("Got uploadUrl: " + uploadUrl);

                // Upload the file
                logger.info("going to upload the file ");
                uploadFile(uploadUrl, fileLocation, dasEndPoint, fileId);
                logger.info("File uploaded successfully!, continue for registration");

                // Check the status of the file upload
                CheckFileStatus checkFileStatus = new CheckFileStatus(dasEndPoint, authToken, fileId);
                String status;
                do {
                    status = checkFileStatus.getFileStatus();
                    logger.info(String.format("File upload status: %s", status));
                    Thread.sleep(5000); // Wait 5 seconds before checking again
                } while (status.equals("processing"));

                if (status.equals("ACCEPTED")) {
                    logger.info("File was accepted!");

                    // Create RegisterFile object
                    RegisterFile registerFile = new RegisterFile(dasEndPoint, authToken, documentCategory,
                            priorityNumber, priorityDate, dasCode,
                            applicationCategory, applicationNumber, applicationDate, fileId);

                    // Register file
                    String acknowledgeId = registerFile.registerFile();
                    logger.warn("File registered with AckId: " + acknowledgeId);
                    updateCsvFile(rowCounter, new Integer(myConfigManager.getConfig().getProperty("columnFileId")), fileId,
                            new Integer(myConfigManager.getConfig().getProperty("columnRegistered")), "true",
                            new Integer(myConfigManager.getConfig().getProperty("columnAckId")), acknowledgeId);

                        

                } else if (status.equals("REJECTED")) {
                    logger.error("File was rejected!");
                }
            } else {
                logger.error("Failed to obtain file ID and upload URL.");
                System.exit(1);
            }

            rowCounter++; // Increment the counter variable by 1 with each iteration
        }
    }

    /**
     * Builds a client assertion, exchanges it for an access token, and returns the token string.
     *
     * @return OAuth2 access token string or {@code null} on error.
     */
    private static String getAuthorizationToken() {
        try {
            JwtAssertionGenerator jwtAssertionGenerator = new JwtAssertionGenerator(myConfigManager);
            String assertion = jwtAssertionGenerator.generateAssertion();
            logger.info("---------------------------------");
            logger.info("JWT Assertion "+ assertion);
            logger.info("---------------------------------");
            String accessToken = GetToken.getAccessToken(assertion,myConfigManager.getConfig().getProperty("issuer"),myConfigManager.getConfig().getProperty("scope"));
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode jsonNode = objectMapper.readTree(accessToken);
            String myToken = jsonNode.get("access_token").asText();

            logger.info("Access token: " + myToken);
            logger.info("---------------------------------");
            logger.info("Expires in: " + jsonNode.get("expires_in") + " secondes.");
            logger.info("---------------------------------");
            return myToken;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }


    /**
     * Computes the SHA-256 checksum of the file.
     */
    private static String calculateSha256(String filePath) throws IOException,NoSuchAlgorithmException {
        return ObtainFileIdAndUploadUrl.getFileChecksum(filePath);
    } 


    /**
     * Uploads a file using a pre-signed URL, auto-refreshing the URL if necessary.
     */
    private static void uploadFile(String uploadUrl, String fileLocation, String dasEndPoint, String fileId) {
        UploadFileToDas uploadFileToDas = new UploadFileToDas(uploadUrl, fileLocation);

        try {
            Integer myUploadResponse = UploadFileToDas.uploadMyFile();
            if (myUploadResponse.equals(200)) {
                logger.warn("File uploaded successfully!");
            } else {
                logger.error("Failed to upload file. Response code: " + myUploadResponse);
                logger.warn("I'll try again with an updated url");
                // If the upload URL has expired, request an updated URL
                // Retrieve updated authorization token
                logger.info("Going to retrieve the access token from the oauth server");
                String myAccessToken = getAuthorizationToken();
                GetUpdatedUploadUrl getUpdatedUploadUrl = new GetUpdatedUploadUrl(dasEndPoint, myAccessToken);
                String updatedUrl = getUpdatedUploadUrl.getUpdatedUrl(fileId);
                if (updatedUrl != null) {
                    logger.warn("Obtained updated URL: " + updatedUrl);
                    uploadFileToDas = new UploadFileToDas(updatedUrl, fileLocation);
                    Integer mySecondUploadResponse = UploadFileToDas.uploadMyFile();
                    if (mySecondUploadResponse.equals(200)) {
                        logger.warn("File was finally uploaded successfully!");
                    } else {
                        logger.error("Failed to upload file. Response code: " + mySecondUploadResponse);
                        System.exit(1);
                    }
                } else {
                    logger.error("Failed to obtain updated URL");
                    System.exit(1);
                }
            }
        } catch (IOException e) {
            logger.error("Failed to upload file: " + e.getMessage());
            System.exit(1);
        }
    }

    /**
     * Helper to update file id, registered flag, and ack id in the registration CSV.
     */
    public static void updateCsvFile(int row, int columnFileId, String fileId, int columnRegistered, String registered, int columnAckId, String ackId) throws Exception {
        try {

            // Update fileId and registered and ackId in the specified row
            myConfigManager.updateCsvData(row, columnFileId, fileId);
            myConfigManager.updateCsvData(row, columnRegistered, registered);
            myConfigManager.updateCsvData(row, columnAckId, ackId);

            // Log the update
            logger.info("CSV file updated successfully");

        } catch (Exception e) {
            logger.error("Failed to update CSV file", e);
            System.exit(1);
        }
    }


        
    }
