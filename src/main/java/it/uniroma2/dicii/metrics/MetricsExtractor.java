package it.uniroma2.dicii.metrics;

import com.github.mauricioaniche.ck.CK;
import com.github.mauricioaniche.ck.CKClassResult;
import com.github.mauricioaniche.ck.CKMethodResult;
import com.github.mauricioaniche.ck.CKNotifier;
import it.uniroma2.dicii.properties.PropertiesManager;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;

@Slf4j
public class MetricsExtractor {

    private static final String HASHING_ALGORITHM = "SHA-256"; // or "MD5", "SHA-1", "SHA-512", ...

    private final String repoPath;
    private final String outputPath;

    private Map<String, CKMethodResult> methodResults;

    public MetricsExtractor() {
        repoPath = PropertiesManager.getInstance().getProperty("project.repo.path");
        outputPath = PropertiesManager.getInstance().getProperty("project.output.path");
    }

    /**
     * Extracts the metrics from the repository using the default CK
     */
    public void extractMetrics() {
        extractMetricsWithCK(new CK());
    }

    /**
     * Extracts the metrics from the repository using the CK with the specified parameters
     *
     * @param useJars            specifies if Jars should be used
     * @param maxAtOnce          max number of elements to analyze at once
     * @param variablesAndFields specifies if variables and fields should be analyzed
     */
    public void extractMetrics(Boolean useJars, Integer maxAtOnce, Boolean variablesAndFields) {
        extractMetricsWithCK(new CK(useJars, maxAtOnce, variablesAndFields));
    }

    /**
     * Extracts the metrics from the repository using the specified CK
     *
     * @param ck the CK object to use for the extraction
     */
    private void extractMetricsWithCK(CK ck) {
        Map<String, CKMethodResult> methodResults = new HashMap<>();

        ck.calculate(repoPath, new CKNotifier() {
            @Override
            public void notify(CKClassResult classResult) {
                for (CKMethodResult methodResult : classResult.getMethods()) {
                    methodResults.put(getMethodName(methodResult), methodResult);
                }
            }

            @Override
            public void notifyError(String sourceFilePath, Exception e) {
                log.error("Error analyzing file: {}. Exception: {} (cause: {})", sourceFilePath, e.getClass(), e.getMessage());
            }

            /**
             * Extracts the method name from the class and method results, generating a final name in the form of
             * <code>path.to.class.methodName#hash</code>. The trailing hash is needed to avoid
             *
             * @param methodResult  the extracted method result
             * @return              the complete, unique method name
             */
            private String getMethodName(CKMethodResult methodResult) {
                String methodName = methodResult.getQualifiedMethodName().split("/")[0] + "#";
                MessageDigest digest;
                String hash;
                try {
                    digest = MessageDigest.getInstance(HASHING_ALGORITHM);
                    byte[] hashBytes = digest.digest(methodResult.getMethodName().getBytes(StandardCharsets.UTF_8));
                    hash = bytesToHex(hashBytes);
                } catch (NoSuchAlgorithmException e) {
                    log.error("Unable to create SHA-256 digest: {}", e.getMessage());
                    hash = "123456"; // Default "hash"
                }
                return methodName + hash;
            }

            private String bytesToHex(byte[] bytes) {
                StringBuilder sb = new StringBuilder(bytes.length * 2);
                for (byte b : bytes) {
                    // & 0xff to avoid sign extension, then format as two hex digits
                    sb.append(String.format("%02x", b & 0xff));
                }
                return sb.toString();
            }
        });
        this.methodResults = methodResults;
        log.info("Successfully extracted metrics for {} methods", methodResults.size());
    }
}
