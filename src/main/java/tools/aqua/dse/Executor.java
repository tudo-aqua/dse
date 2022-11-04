/*
 * Copyright (C) 2021, Automated Quality Assurance Group,
 * TU Dortmund University, Germany. All rights reserved.
 *
 * DSE (dynamic symbolic execution) is licensed under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0.
 *
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package tools.aqua.dse;

import gov.nasa.jpf.constraints.api.Valuation;
import gov.nasa.jpf.constraints.api.ValuationEntry;
import tools.aqua.dse.trace.Trace;
import tools.aqua.dse.trace.TraceParser;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

public class Executor {

    private String executurCmd;

    private String executorArgs;

    private boolean b64encode;

    public Executor(Config config) {
        this.executurCmd = config.getExecutorCmd();
        this.executorArgs = config.getExecutorArgs();
        this.b64encode = config.isB64encodeExecutorValue();
    }

    public Trace execute(Valuation val) {
        //System.out.println("model: " + val);
        String[] cmd = new String[] {
            this.executurCmd,
            generateParam("concolic.bools", "__bool_", val),
            generateParam("concolic.bytes", "__byte_", val),
            generateParam("concolic.chars", "__char_", val),
            generateParam("concolic.shorts", "__short_", val),
            generateParam("concolic.ints", "__int_", val),
            generateParam("concolic.longs", "__long_", val),
            generateParam("concolic.floats", "__float_", val),
            generateParam("concolic.doubles", "__double_", val),
            generateParam("concolic.strings", "__string_", val),
            this.executorArgs
        };
        System.out.println(String.join(" ", cmd));
        try {
            Path output = Files.createTempFile("dse", "");
            int rc = (new ProcessBuilder())
                    .command(cmd)
                    .redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.to(output.toFile()))
                    .start()
                    .waitFor();

            List<String> lines = Files.readAllLines(output);
//            System.out.println("%%%%%%%%%%% Executor Output Start");
//            lines.forEach(n ->{System.out.println(n);});
//            System.out.println("%%%%%%%%%%% Executor Output End");
            Files.delete(output);
            return TraceParser.parseTrace(lines, val);
        } catch (Throwable t) {
            t.printStackTrace();
            return null;
        }
    }

    private String generateParam(String optionName, String prefix, Valuation val) {
        ArrayList<String> param = new ArrayList<>();
        int max = getMaxVarId(val, prefix);
        for (int i=0; i<=max; i++) {
            String varName = prefix + i;
            Object value = val.getValue(varName);
            String p = (value != null) ? value.toString() : defaultValue(prefix);
            param.add( b64encode ? b64Encode(p) : p);
        }
        return (param.isEmpty()) ? "" : "-D" + optionName + "=" +
                (b64encode ? "[b64]" : "") +
                String.join(",", param);
    }

    private String b64Encode(String p) {
        byte[] in = p.getBytes(StandardCharsets.UTF_8);
        byte[] out = Base64.getEncoder().encode(in);
        return new String(out);
    }

    private String defaultValue(String prefix) {
        switch (prefix) {
            case "__bool_":
            case "__byte_":
            case "__char_":
            case "__short_":
            case "__int_":
            case "__long_":  return "0";
            case "__float_":
            case "__double_": return "0.0";
            case "__string_": return ""; //FIXME: not sure if this works on the other end (zero length string disregarded?)
            default:
                throw new IllegalArgumentException("unsupported prefix for default values: " + prefix);
        }
    }

    private int getMaxVarId(Valuation val, String prefix) {
        int max = -1;
        for (ValuationEntry e : val.entries()) {
            String name = e.getVariable().getName();
            if (name.startsWith(prefix)) {
                int id = Integer.parseInt( name.substring(prefix.length()) );
                max = Math.max(id, max);
            }
        }
        return max;
    }

}
