package it.uniroma2.dicii.properties;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public class PropertiesManager {

    private static final String PROPERTIES_FILE_NAME = "project.properties";

    private static PropertiesManager instance;

    private final Map<String, String> properties;

    private PropertiesManager() {
        properties = new HashMap<>();
        loadProperties();
    }

    public static PropertiesManager getInstance() {
        if (instance == null) {
            instance = new PropertiesManager();
        }

        return instance;
    }

    private void loadProperties() {
        Properties props = new Properties();
        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream(PROPERTIES_FILE_NAME)) {
            if (inputStream != null) {
                props.load(inputStream);
                for (String key : props.stringPropertyNames()) {
                    properties.put(key, props.getProperty(key));
                }
            } else {
                System.err.println("Unable to find " + PROPERTIES_FILE_NAME);
            }
        } catch (IOException e) {
            System.err.println("Error loading properties file: " + e.getMessage());
        }
    }

    public String getProperty(String propertyName) {
        return properties.get(propertyName);
    }

}