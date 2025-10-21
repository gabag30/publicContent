package org.wipo.das.restapitest;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Properties;
import java.util.Arrays;

import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import com.opencsv.CSVWriter;
import com.opencsv.exceptions.CsvException;

/**
 * Centralized configuration and CSV state manager.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Loads {@code config/config.properties} (OAuth, DAS endpoint, CSV column indexes, logging)</li>
 *   <li>Configures Log4j 2 using the path provided by {@code log4jConfigPath}</li>
 *   <li>Loads a CSV file into memory and persists updates back to disk</li>
 * </ul>
 */
public class ConfigManager {

    private Properties config;
    private String[][] csvData;
    private String csvPath;

    private static final Logger logger = LogManager.getLogger(ConfigManager.class);

    /**
     * Loads properties and CSV data; initializes Log4j.
     *
     * @param config_file_path path to {@code config.properties}.
     * @param csv_file_path path to a CSV used by the selected flow.
     * @throws CsvException if the CSV cannot be parsed.
     */
    public ConfigManager(String config_file_path, String csv_file_path) throws CsvException {
        // Load configuration
        try {
            FileReader reader = new FileReader(config_file_path);
            config = new Properties();
            config.load(reader);
            reader.close();

            // Removed unnecessary static variables, use config.getProperty() to access these values

            // configure log4j
            String log4jConfigPath = config.getProperty("log4jConfigPath");
            if (log4jConfigPath != null) {
                File log4jConfigFile = new File(log4jConfigPath);
                if (log4jConfigFile.exists()) {
                    org.apache.logging.log4j.core.config.Configurator.initialize(null, log4jConfigPath);
                } else {
                    logger.error("Log4j configuration file not found at " + log4jConfigPath);
                }
            } else {
                logger.error("No log4j configuration path provided.");
            }
        } catch (IOException e) {
            logger.error("Failed to load configuration file", e);
            System.exit(1);
        }

        // Load CSV file
        try {
            CSVReader reader = new CSVReaderBuilder(new FileReader(csv_file_path))
                    //.withSkipLines(1)
                    .build();
            csvData = reader.readAll().toArray(new String[0][]);
            reader.close();
            csvPath = csv_file_path;
        } catch (IOException e) {
            logger.error("Failed to load CSV file", e);
            System.exit(1);
        }
    }

    /**
     * @return loaded configuration properties.
     */
    public Properties getConfig() {
        return config;
    }

    /**
     * @return entire CSV content as a 2D array (row-major), header included.
     */
    public String[][] getCsvData() {
        return csvData;
    }

    /**
     * @return the path to the associated CSV file on disk.
     */
    public String getCsvPath() {
        return csvPath;
    }

    /**
     * Updates a single cell and writes the whole CSV back to disk.
     *
     * @param row zero-based row index (including header row at index 0).
     * @param column zero-based column index.
     * @param value new value to persist.
     */
    public void updateCsvData(int row, int column, String value) {
        csvData[row][column] = value;
        // Update CSV file
        try {
            CSVWriter writer = new CSVWriter(new FileWriter(csvPath));
            // writer.writeNext(new String[] { "file_reference", "file_location", "application_number",
            //         "application_date", "priority_number", "priority_date", "document_category",
            //         "application_category", "das_code", "file_id", "registered","ack_id" });
            writer.writeAll(Arrays.asList(csvData));
            writer.close();
        } catch (IOException e) {
            logger.error("Failed to update CSV file", e);
            System.exit(1);
        }
    }

    /**
     * @return shared Log4j 2 logger.
     */
    public static Logger getLogger () {
        return logger;
    }
}
