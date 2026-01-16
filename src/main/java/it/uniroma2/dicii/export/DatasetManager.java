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
            fileWriter.write(HEADER);
        } catch (IOException e) {
            log.error("Error while creating dataset: {}", e.getMessage());
        }
    }

    public void appendToDataset(String version, List<MeasuredMethod> measuredMethods) throws IOException {
        try (FileWriter fileWriter = new FileWriter(datasetPath)) {
            for (MeasuredMethod method : measuredMethods)
                fileWriter.write(version + "," + method.toCsvRow() + "\n");
            log.info("Dataset updated successfully ({} rows written)", measuredMethods.size());
        } catch (IOException e) {
            log.error("Error while exporting dataset: {}", e.getMessage());
        }
    }

}
