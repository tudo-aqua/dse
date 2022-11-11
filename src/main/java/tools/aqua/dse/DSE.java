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
import org.stringtemplate.v4.ST;
import org.stringtemplate.v4.STGroup;
import org.stringtemplate.v4.STRawGroupDir;
import tools.aqua.dse.paths.PathResult;
import tools.aqua.dse.trace.ResultWitnessAssumption;
import tools.aqua.dse.trace.Trace;
import tools.aqua.dse.trace.WitnessAssumption;
import tools.aqua.dse.witness.ResultWitnessEdge;
import tools.aqua.dse.witness.WitnessEdge;
import tools.aqua.dse.witness.WitnessNode;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class DSE {

    private final Config config;

    private boolean savedWitness = false;

    public DSE(Config config) {
        this.config = config;
    }

    public void executeAnalysis() {
        Explorer explorer = new Explorer(config);
        Executor executor = new Executor(config);

        while (explorer.hasNextValuation()) {
            Valuation val = explorer.getNextValuation();
            Trace trace = executor.execute(val);
            if (trace != null) {
                trace.print();
            } else {
                System.out.println("== no trace obtained.");
            }
            explorer.addTrace(trace);

            // check if we should save a witness
            checkAndSaveWitness(trace);
        }

        System.out.println(explorer.getAnalysis());
        System.out.println("[END OF OUTPUT]");
        System.exit(0);
    }

    /*
     *
     *
     */

    private void checkAndSaveWitness(Trace trace) {
        if (!config.isWitness() || savedWitness ||
                !(trace.getTraceState() instanceof PathResult.ErrorResult) ||
                !trace.hasWitness()) {
            return;
        }
        PathResult.ErrorResult res = (PathResult.ErrorResult) trace.getTraceState();
        if (!res.getExceptionClass().equals("java/lang/AssertionError") && !res.getExceptionClass().equals("error encountered")) {
            return;
        }

        savedWitness = true;

        List<WitnessNode> nodes = new ArrayList<>();
        List<WitnessEdge> edges = new ArrayList<>();
        List<ResultWitnessEdge> resultEdges = new ArrayList<>();

        int nodeId = 0;
        WitnessNode initNode = new WitnessNode(nodeId++);
        nodes.add(initNode);
        initNode.addData("entry", "true");
        WitnessNode curNode = initNode;

        if (!trace.getWitness().isEmpty() && !trace.getResultWitness().isEmpty()) {
            throw new IllegalStateException("cannot handle multiple witness types");
        }

        for (WitnessAssumption wa : trace.getWitness()) {
            String loc = getLineOfCode(wa.getClazz(), wa.getLine());
            String assumption = computeAssumption(loc, wa);

            WitnessNode prevNode = curNode;
            curNode = new WitnessNode(nodeId++);
            nodes.add(curNode);
            WitnessEdge edge = new WitnessEdge(prevNode, wa, assumption, curNode);
            edges.add(edge);
        }

        for (ResultWitnessAssumption rwa : trace.getResultWitness()) {
            WitnessNode prevNode = curNode;
            curNode = new WitnessNode(nodeId++);
            nodes.add(curNode);
            ResultWitnessEdge edge = new ResultWitnessEdge(prevNode, rwa, curNode);
            resultEdges.add(edge);
        }

        curNode.addData("violation", "true");

        STGroup group = new STRawGroupDir("witnesses", '$','$');
        ST st = group.getInstanceOf("witness");
        st.add("nodes", nodes);
        st.add("edges", edges);
        st.add("resultedges", resultEdges);
        String result = st.render();
        try {
            Files.write(Paths.get("witness.graphml"), result.getBytes());
        } catch (IOException e) {
            System.err.println("Error writing witness to file: " + e.getMessage());
        }
    }

    private String computeAssumption(String lineOfCode, WitnessAssumption wa) {
        if (lineOfCode == null) {
            return "true";
        }
        int idx = lineOfCode.indexOf("Verifier");
        if (idx < 0) {
            return "true";
        }
        lineOfCode = lineOfCode.substring(0, idx).trim();
        idx = lineOfCode.lastIndexOf("=");
        if (idx < 0) {
            return "true";
        }
        lineOfCode = lineOfCode.substring(0, idx).trim();
        String[] parts = lineOfCode.split(" ");
        String id = parts[parts.length-1].trim();
        if (wa.getValue().contains("\"") && !wa.getValue().contains("parse")) {
            // its a string constant
            return "" + id + ".equals(" + wa.getValue() + ")";
        }
        return "" + id +" = " + wa.getValue();
    }

    private String getLineOfCode(String filename, int line) {
        try {
            InputStream is = config.getSourceLoader().getResourceAsStream(filename);
            if (is == null) {
                return null;
            }
            BufferedReader res = new BufferedReader(new InputStreamReader(is));
            String loc = "";
            for (int i = 0; i<line; i++) {
                loc = res.readLine();
            }
            return loc;
        } catch (IOException e) {
            return null;
        }
    }
}
