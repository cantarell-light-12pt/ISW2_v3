package it.uniroma2.dicii.metrics.impl;

import com.github.mauricioaniche.ck.CK;
import com.github.mauricioaniche.ck.CKClassResult;
import com.github.mauricioaniche.ck.CKMethodResult;
import com.github.mauricioaniche.ck.CKNotifier;
import it.uniroma2.dicii.metrics.MetricsExtractor;
import it.uniroma2.dicii.metrics.model.MeasuredMethod;
import it.uniroma2.dicii.properties.PropertiesManager;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class CKMetricsExtractor implements MetricsExtractor {

    private static final String HASHING_ALGORITHM = "SHA-256";

    private final String repoPath;

    private Map<String, CKMethodResult> methodResults;

    public CKMetricsExtractor() {
        repoPath = PropertiesManager.getInstance().getProperty("project.repo.path");
    }

    public List<MeasuredMethod> extractMetrics() {
        Map<String, CKMethodResult> extractedMetrics = extractMetricsWithCK(new CK());
        return null;
    }

    public List<MeasuredMethod> extractMetrics(Boolean useJars, Integer maxAtOnce, Boolean variablesAndFields) {
        Map<String, CKMethodResult> extractedMetrics = extractMetricsWithCK(new CK(useJars, maxAtOnce, variablesAndFields));
        return null;
    }

    /**
     * Extracts the metrics from the repository using the specified CK
     *
     * @param ck the CK object to use for the extraction
     */
    private Map<String, CKMethodResult> extractMetricsWithCK(CK ck) {
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
                return methodName + "#" + hash;
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
        log.info("Successfully extracted metrics for {} methods", methodResults.size());
        return methodResults;
    }
}
