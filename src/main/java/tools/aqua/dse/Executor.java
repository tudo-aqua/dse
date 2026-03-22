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
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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

    public Trace execute(Valuation val, Config config) {
//        System.out.println("model: " + val);
        List<String> chosenConstructors = extractChosenConstructors(val);

        String constructors = generateConstructors(val);

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
            constructors,
//            generateConstructorCount(chosenConstructors),
//            generateConstructorIds(chosenConstructors),
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
            return TraceParser.parseTrace(lines, val, config);
        } catch (Throwable t) {
            t.printStackTrace();
            return null;
        }
    }

    private String generateParam(String optionName, String prefix, Valuation val) {
        ArrayList<String> param = generateParameterList(prefix, val);
        return (param.isEmpty()) ? "" : "-D" + optionName + "=" +
                (b64encode ? "[b64]" : "") +
                String.join(",", param);
    }

    private ArrayList<String> generateParameterList(String prefix, Valuation val) {
        ArrayList<String> param = new ArrayList<>();
        int max = getMaxVarId(val, prefix);
        for (int i=0; i<=max; i++) {
            String varName = prefix + i;
            Object value = val.getValue(varName);
            String p = (value != null) ? value.toString() : defaultValue(prefix);
            param.add( b64encode ? b64Encode(p) : p);
        }
        return param;
    }

    public String generateConstructors(Valuation val) {

        Pattern pattern = Pattern.compile("__object_(\\d+)\\.init");

        List<String> constructorValues = val.entries().stream()
                .filter(valuationEntry -> pattern.matcher(valuationEntry.getVariable().getName()).find())
                .sorted(Comparator.comparing(entry -> entry.getVariable().getName()))
                .map(ValuationEntry::getValue)
                .map(s -> (String) s)
                .toList();

        Pattern pattern2 = Pattern.compile("__object_\\d+__(bool|byte|char|short|int|long|float|double|string)_\\d+");


        List<String> modifiedConstructorValues = new ArrayList<>();
        for (String constructorValue : constructorValues) {
            Matcher matcher = pattern2.matcher(constructorValue);
            boolean replaced = false;
            while (matcher.find()) {
                String primitive = matcher.group();

                Optional<?> valueOfPrimitive = val.entries().stream()
                        .filter(valuationEntry -> valuationEntry.getVariable().getName().equals(primitive))
                        .findFirst()
                        .map(ValuationEntry::getValue);
                
                if (valueOfPrimitive.isEmpty()) {
                    throw new RuntimeException("Could not find valueOfPrimitive for " + primitive);
                }

                modifiedConstructorValues.add(constructorValue.replace(primitive, valueOfPrimitive.get().toString()));
                replaced = true;
            }
            if (!replaced) {
                modifiedConstructorValues.add(constructorValue);
            }
        }

        if (modifiedConstructorValues.isEmpty()) {
            return "";
        }

        return String.format("-Dconcolic.constructors=%s",
                String.join(",", modifiedConstructorValues));
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
        return this.clazzModel.getConstructorsOfAllClasses().size()+1;
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
