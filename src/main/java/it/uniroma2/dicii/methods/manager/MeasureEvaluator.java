package it.uniroma2.dicii.methods.manager;

import it.uniroma2.dicii.methods.model.ProjectMethod;

public interface MeasureEvaluator  {

    /**
     * Evaluates the measure for the given method and adds it to the method's measures map.
     * If the map already contains the measure, it is overwritten.
     *
     * @param method the method for which the measure is evaluated
     * @param measureName the name of the measure to be evaluated
     */
    void evaluateMeasure(ProjectMethod method, String measureName);

}
