package tools.aqua.dse;

import gov.nasa.jpf.constraints.api.Valuation;
import tools.aqua.dse.trace.Trace;
import tools.aqua.dse.trace.TraceParser;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class Executor {

    private String executurCmd;

    private String targetClass;

    private String targetClasspath;

    public Executor(Config config) {
        this.executurCmd = config.getExecutorCmd();
        this.targetClass = config.getTargetClass();
        this.targetClasspath = config.getTargetClasspath();
    }

    public Trace execute(Valuation val) {
        String[] cmd = new String[] {
            this.executurCmd,
            "-cp " + this.targetClasspath,
            generateParam("concolic.ints", "__int_", val),
            generateParam("concolic.strings", "__string_", val),
            this.targetClass
        };
        System.out.println(String.join(" ", cmd));
        try {
            Path output = Files.createTempFile("dse", "");
            int rc = (new ProcessBuilder())
                    .command(cmd)
                    .redirectOutput(ProcessBuilder.Redirect.to(output.toFile()))
                    .start()
                    .waitFor();

            List<String> lines = Files.readAllLines(output);
            Files.delete(output);
            return TraceParser.parseTrace(lines, val);
        } catch (Throwable t) {
            t.printStackTrace();
            return null;
        }
    }

    private String generateParam(String optionName, String prefix, Valuation val) {
        ArrayList<String> param = new ArrayList<>();
        int i = 0;
        Object obj = null;
        while ((obj = val.getValue(prefix + i)) != null) {
            param.add( obj.toString() );
            i++;
        }
        return (param.isEmpty()) ? "" : "-D" + optionName + "=" + String.join(",", param);
    }

}
