package tools.aqua.dse.witness.svcomp;

public class WitnessProducer {

        public String name;
        public String version;
        public String configuration;
        public String commandLine;
        public String description;
        WitnessProducer(String pName, String pVersion, String pConf, String pCmd, String pDescription) {
            name = pName;
            version = pVersion;
            configuration = pConf;
            commandLine = pCmd;
            description = pDescription;
        }

        public static WitnessProducer createMinimalProducer(String pName, String pVersion){
            return new WitnessProducer(pName,pVersion,null,null,null);
        }
}
