package tools.aqua.dse.witness;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import tools.aqua.dse.witness.svcomp.ViolationSequence;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.dataformat.yaml.YAMLFactory;
import tools.jackson.dataformat.yaml.YAMLMapper;

import java.io.StringWriter;
import java.io.Writer;

public class WitnessSerializationTest {
    ObjectMapper mapper;
    @BeforeClass
    public void setup() {
         mapper = YAMLMapper.builder().changeDefaultPropertyInclusion(inclusion ->
                         inclusion.withValueInclusion(JsonInclude.Include.NON_NULL)).
                 propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE).build();
    }

    @Test
    public void serializeViolationSequence(){
        ViolationSequence violationSequence = new ViolationSequence();
        Writer w = new StringWriter();
        mapper.writeValue(w, violationSequence);
        String result = w.toString();
        assert result.contains("entry_type");
    }
}
