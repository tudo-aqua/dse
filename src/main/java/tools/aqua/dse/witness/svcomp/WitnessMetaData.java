package tools.aqua.dse.witness.svcomp;

import java.time.LocalDateTime;
import java.util.UUID;

public class WitnessMetaData
{

    public String formatVersion;
    public String uuid;
    public LocalDateTime creationTime;
    public WitnessProducer producer;
    public WitnessTask task;
    public static WitnessMetaData createMetaDataFormat2_2(WitnessProducer prod, WitnessTask task){
        WitnessMetaData meta = new WitnessMetaData();
        meta.formatVersion = "2.2";
        meta.uuid = UUID.randomUUID().toString();
        meta.creationTime = LocalDateTime.now();
        meta.producer = prod;
        meta.task = task;
        return meta;
    }
}
