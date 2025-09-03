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
import tools.aqua.dse.objects.ClazzModel;
import tools.aqua.dse.trace.Trace;
import tools.aqua.dse.trace.TraceParser;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;

public class Executor {

    private String executurCmd;

    private String executorArgs;

    private boolean b64encode;

    private ClazzModel clazzModel;

    public Executor(Config config) {
        this.executurCmd = config.getExecutorCmd();
        this.executorArgs = config.getExecutorArgs();
        this.b64encode = config.isB64encodeExecutorValue();
        this.clazzModel = config.getClazzModel();
    }

    public Trace execute(Valuation val) {
//        System.out.println("model: " + val);
        List<String> chosenConstructors = extractChosenConstructors(val);
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
            generateParam("concolic.constructors", "__object_constructor_", val),
            generateConstructorCount(chosenConstructors),
            generateConstructorIds(chosenConstructors),
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
            System.out.println("\033[34m%%%%%%%%%%% Executor Output Start");
            lines.forEach(System.out::println);
            System.out.println("%%%%%%%%%%% Executor Output End\033[0m");
            Files.delete(output);
            return TraceParser.parseTrace(lines, val, clazzModel);
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

    /**
     * Generates a system property string that defines constructor IDs for concolic execution.
     * <p>
     * The method takes a list of constructor signatures, looks up their
     * corresponding internal constructor IDs via {@code clazzModel.findConstructorId}, and
     * concatenates them into a comma-separated string. The resulting value is prefixed with
     * {@code -Dconcolic.constructorIds=} so it can be passed as a JVM system property.
     * </p>
     *
     * @param chosenConstructors a list of constructor signatures or names to resolve into IDs;
     *                           may be {@code null} or empty
     * @return a system property string in the form {@code -Dconcolic.constructorIds=<id1,id2,...>};
     *         if the list is {@code null} or empty, the result will end with an empty assignment
     *         (e.g., {@code -Dconcolic.constructorIds=})
     */
    private String generateConstructorIds(List<String> chosenConstructors) {
        if (chosenConstructors.isEmpty()) {
            return "-Dconcolic.constructorIds=0";
        }
        // Map each chosen constructor to its ID and join them into a comma-separated string
        String ids = chosenConstructors.stream()
                .map(clazzModel::findConstructorIdInAllConstructors)                 // find id of the constructor
                .map(String::valueOf)                               // convert number to String
                .collect(Collectors.joining(","));         // join with commas

        return "-Dconcolic.constructorIds=" + ids;
    }

    /**
     * Generates a system property string that defines constructor count for concolic execution.
     * <p>
     * The method takes a list of constructor signatures, looks up how many constructors belong to the clazz of the
     * constructor via {@code clazzModel.findConstructorCount}, and
     * concatenates them into a comma-separated string. The resulting value is prefixed with
     * {@code -Dconcolic.constructorIds=} so it can be passed as a JVM system property.
     * </p>
     *
     * @param chosenConstructors a list of constructor signatures or names to resolve into IDs;
     *                           may be {@code null} or empty
     * @return a system property string in the form {@code -Dconcolic.constructorIds=<id1,id2,...>};
     *         if the list is {@code null} or empty, the result will end with an empty assignment
     *         (e.g., {@code -Dconcolic.constructorCounts=})
     */
    private String generateConstructorCount(List<String> chosenConstructors) {
//        if (chosenConstructors.isEmpty()) {
//            return "";
//        }
//
//        String counts = chosenConstructors.stream()
//                .map(clazzModel::findConstructorCount)          // find number of the constructors in the clazz to which this constructor belongs
//                .map(String::valueOf)                           // convert number to string
//                .collect(Collectors.joining(","));     // join with commas

        return "-Dconcolic.constructorCounts=" + getConstructorCount();
    }

    private int getConstructorCount() {
        return this.clazzModel.getConstructorsOfAllClasses().size();
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
            case "__long_":
            case "__object_constructor_id_": return "0";
            case "__float_":
            case "__double_": return "0.0";
            case "__string_": return ""; //FIXME: not sure if this works on the other end (zero length string disregarded?)
            case "__constructor_": return "null";
            case "__object_constructor_count_": return "1";
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

    private List<String> extractChosenConstructors(Valuation val) {
        List<String> chosen = new ArrayList<>();
        int max = getMaxVarId(val, "__object_constructor_");
        for (int i = 0; i <= max; i++) {
            String varName = "__object_constructor_" + i;
            Object value = val.getValue(varName);
            if (value != null) {
                chosen.add(value.toString());
            }
        }
        return chosen;
    }


}
