package it.uniroma2.dicii.metrics.impl;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MethodNameGenerator {

    /**
     * Generates a unique method name by combining the provided fully qualified method name
     * with a hash generated from its parameter types. If the method name includes parameter types,
     * they are included in the hash computation; otherwise, a default hash value is used.
     *
     * @param fullQualifiedMethodName the full qualified method name consisting of the class name
     *                                and optionally the parameter types
     * @return a string representing the unique method name in the format "package.Class.method#parametersHash"
     */
    public static String generateMethodName(String fullQualifiedMethodName, int startLine) {
        return String.format("%s#%d", fullQualifiedMethodName, startLine);
    }

}
