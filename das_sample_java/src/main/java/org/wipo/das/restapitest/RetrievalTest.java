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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import java.security.NoSuchAlgorithmException;

public class RetrievalTest {

    private static final Logger logger = ConfigManager.getLogger();
    private static ConfigManager myConfigManager;

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

        
        String dasEndPoint = myConfigManager.getConfig().getProperty("url");


        String[][] csvData = myConfigManager.getCsvData();

        int rowCounter = 1; // Initialize counter variable to 1
        // Process each row in the CSV data
        for (String[] nextLine : Arrays.copyOfRange(csvData, 1, csvData.length) ){
            String priorityNumber = nextLine[0];
            String priorityDate = nextLine[1];
            String documentCategory = nextLine[2];
            String dasCode = nextLine[3];
            String ackId = nextLine[4];
            String downloaded = nextLine[5];


            // Skip already downloaded  files
            if (downloaded.equalsIgnoreCase("true")) {
                logger.info(String.format("File '%s' was already downloaded.", priorityNumber));
                continue;
            }

            // check if the ack was already requested (but file was not downloaded)

            if (ackId == null || ackId.isEmpty()) {

                
                // Get the acknowledgment Id for the retrieval of the file
                GetOsfAckId getOsfAckId = new GetOsfAckId(dasEndPoint, authToken, documentCategory,
                        priorityNumber, priorityDate, dasCode);

                // Register retrieval
                ackId = getOsfAckId.getAck();
                logger.warn("File registered with AckId: " + ackId);
                
            }

            // check if file is ready to download:

            //send request to retrieve the document the request should reply the url or not
            //in case the url is provided proceed to download then updated the csv file with the true for downloaded

            //in case the files is still not ready, update the csv file with the ackid and false(for download)





            // updateCsvFile(myConfigManager, rowCounter, new Integer(myConfigManager.getConfig().getProperty("columnFileId")), fileId,
            //         new Integer(myConfigManager.getConfig().getProperty("columnRegistered")), "true",
            //         new Integer(myConfigManager.getConfig().getProperty("columnAckId")), acknowledgeId);


            

            // if (osfAckId != null) {

            //     fileId = fileIdAndUrl[0];
            //     String uploadUrl = fileIdAndUrl[1];

            //     logger.warn("Got fileId: " + fileId);
            //     logger.warn("Got uploadUrl: " + uploadUrl);

            //     // Upload the file
            //     logger.info("going to upload the file ");
            //     uploadFile(uploadUrl, fileLocation, dasEndPoint, fileId);
            //     logger.info("File uploaded successfully!, continue for registration");

            //     // Check the status of the file upload
            //     CheckFileStatus checkFileStatus = new CheckFileStatus(dasEndPoint, authToken, fileId);
            //     String status;
            //     do {
            //         status = checkFileStatus.getFileStatus();
            //         logger.info(String.format("File upload status: %s", status));
            //         Thread.sleep(5000); // Wait 5 seconds before checking again
            //     } while (status.equals("processing"));

            //     if (status.equals("ACCEPTED")) {
            //         logger.info("File was accepted!");

                  

            //     } else if (status.equals("REJECTED")) {
            //         logger.error("File was rejected!");
            //     }
            // } else {
            //     logger.error("Failed to obtain file ID and upload URL.");
            //     System.exit(1);
            // }

            rowCounter++; // Increment the counter variable by 1 with each iteration
        }
    }

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


    private static String calculateSha256(String filePath) throws IOException,NoSuchAlgorithmException {
        return ObtainFileIdAndUploadUrl.getFileChecksum(filePath);
    } 


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

    public static void updateCsvFile(ConfigManager configManager, int row, int columnFileId, String fileId, int columnRegistered, String registered, int columnAckId, String ackId) throws Exception {
        try {

            // Update fileId and registered and ackId in the specified row
            configManager.updateCsvData(row, columnFileId, fileId);
            configManager.updateCsvData(row, columnRegistered, registered);
            configManager.updateCsvData(row, columnAckId, ackId);

            // Log the update
            configManager.getLogger().info("CSV file updated successfully");

        } catch (Exception e) {
            ConfigManager.getLogger().error("Failed to update CSV file", e);
            System.exit(1);
        }
    }


        
    }
