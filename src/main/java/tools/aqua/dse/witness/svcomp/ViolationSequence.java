package tools.aqua.dse.witness.svcomp;

import tools.aqua.dse.trace.Trace;
import tools.aqua.dse.trace.WitnessAssumption;

import java.util.ArrayList;

public class ViolationSequence {
    public String entryType = "violation_sequence";
    public WitnessMetaData metadata;
    public WitnessSegment[] content;
    public static ViolationSequence createViolationSequenceFromTrace(Trace t, WitnessTask task) {
        ViolationSequence violationSequence = new ViolationSequence();
        WitnessProducer p = WitnessProducer.createMinimalProducer("GDart", "VERSION_PLACEHOLDER");
        violationSequence.metadata = WitnessMetaData.createMetaDataFormat2_2(p, task);
        ArrayList<WitnessSegment> wit = new ArrayList<>();
        for(WitnessAssumption wa :t.getWitness()){
            WitnessLocation wl = new WitnessLocation(wa.getClazz(), wa.getLine(), wa.getScope());
            wit.add(new FunctionReturnSegment("\\result == " + wa.getValue(), wl));
        }
        violationSequence.content = wit.toArray(new WitnessSegment[]{});
        return violationSequence;
    }
}
