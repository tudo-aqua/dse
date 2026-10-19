package tools.aqua.dse.witness;

import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import tools.aqua.dse.witness.svcomp.ViolationSequence;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.dataformat.yaml.YAMLFactory;

import java.io.StringWriter;
import java.io.Writer;

public class WitnessSerializationTest {
    ObjectMapper mapper;
    @BeforeClass
    public void setup() {
         mapper = new ObjectMapper(new YAMLFactory());
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
