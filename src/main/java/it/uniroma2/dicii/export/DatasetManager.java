package it.uniroma2.dicii.export;

import it.uniroma2.dicii.metrics.model.MeasuredMethod;
import lombok.extern.slf4j.Slf4j;

import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

@Slf4j
public class DatasetManager {

    private static final String HEADER = "version,method,CyC,CoC,MND,JD,SLOC,NP,CD,FI,FO,CH,PD,DC,BS,HS,MS,LS,IS,buggy";

    private final String datasetPath;

    public DatasetManager(String datasetPath) {
        this.datasetPath = datasetPath;
    }

    public void initDataset() {
        try (FileWriter fileWriter = new FileWriter(datasetPath)) {
            fileWriter.write(HEADER + "\n");
        } catch (IOException e) {
            log.error("Error while creating dataset: {}", e.getMessage());
        }
    }

    public void appendToDataset(String version, List<MeasuredMethod> measuredMethods) {
        try (FileWriter fileWriter = new FileWriter(datasetPath, true)) {
            for (MeasuredMethod method : measuredMethods)
                fileWriter.append(version).append(",").append(method.toCsvRow()).append("\n");
            log.info("Dataset updated successfully ({} rows written)", measuredMethods.size());
        } catch (IOException e) {
            log.error("Error while exporting dataset: {}", e.getMessage());
        }
    }

}
