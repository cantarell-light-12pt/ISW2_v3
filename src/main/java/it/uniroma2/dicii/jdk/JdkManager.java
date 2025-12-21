package it.uniroma2.dicii.jdk;

import it.uniroma2.dicii.properties.PropertiesManager;

public class JdkManager {

    /**
     * Resolves the file system path to the JDK installation directory for the given Java version.
     * The version-specific path is retrieved from a properties file, and if not found,
     * a default JDK path is returned, if available.
     *
     * @param version the Java version for which the JDK path needs to be resolved, e.g., "1.8" or "8"
     * @return the file system path to the JDK installation for the specified version,
     * or null if neither a version-specific nor a default JDK path is configured
     */
    public static String getJdkPathForVersion(String version) {
        // Handle common formats: "1.8" -> "jdk.1.8.home", "8" -> "jdk.8.home"
        String propKey = "jdk." + version + ".home";
        String path = PropertiesManager.getInstance().getProperty(propKey);

        if (path == null) {
            // Try to fallback to default
            path = PropertiesManager.getInstance().getProperty("jdk.default.home");
        }
        return path;
    }

}
