package tools.aqua.dse.witness.svcomp;

import com.fasterxml.jackson.annotation.JsonInclude;
import gov.nasa.jpf.constraints.api.Valuation;
import tools.aqua.dse.Config;
import tools.aqua.dse.trace.Trace;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.PropertyNamingStrategy;
import tools.jackson.dataformat.yaml.YAMLFactory;
import tools.jackson.dataformat.yaml.YAMLMapper;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class SVCompViolationWitnessFile {

    List<ViolationSequence> items = new ArrayList<>();

    public static SVCompViolationWitnessFile createViolationWitnessFromTrace(Trace t, Config config) {
        if(!t.hasWitness())
            throw new IllegalArgumentException("The given Trace does not have a witness");
        SVCompViolationWitnessFile wit = new SVCompViolationWitnessFile();
        WitnessTask task = WitnessTask.createTask(config);
        wit.items.add(ViolationSequence.createViolationSequenceFromTrace(t, task));
        return wit;
    }

    public void writeToFile(Path dest){
        YAMLMapper mapper = YAMLMapper.builder().changeDefaultPropertyInclusion(inclusion ->
                inclusion.withValueInclusion(JsonInclude.Include.NON_NULL)).
                propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE).build();
        mapper.writeValue(dest, items);
        System.out.println("Witness file has been written to: " + dest.toString());
    }
}
