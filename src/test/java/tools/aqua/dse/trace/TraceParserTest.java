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

package tools.aqua.dse.trace;

import gov.nasa.jpf.constraints.api.Valuation;
import gov.nasa.jpf.constraints.smtlibUtility.parser.SMTLIBParserException;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import tools.aqua.dse.Config;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;

public class TraceParserTest {
    private static Config config;
    @BeforeClass
    public static void setup() {
        Properties props = new Properties();
        props.setProperty("dse.executor", "testOnly");
        props.setProperty("dse.dp", "z3");

        TraceParserTest.config = Config.fromProperties(props);
    }
    @Test
    public void testTraceParser() throws IOException, SMTLIBParserException {
        List<String> log = new LinkedList<>();
        log.add("======================== START PATH [BEGIN].");
        log.add("Seeded Int Values: [0]");
        log.add("======================== START PATH [END].");
        log.add("Exception in thread \"main\" java.lang.ArrayIndexOutOfBoundsException");
        log.add("       at Test9.main(Test9.java:9)");
        log.add("======================== END PATH [BEGIN].");
        log.add("[DECLARE] (declare-fun __int_0 () Int)");
        log.add("[DECISION] (assert (<= 50 (+ 11 __int_0))) // branchCount=2, branchId=1");
        log.add("[ERROR] java.lang.ArrayIndexOutOfBoundsException");
        log.add("======================== END PATH [END].");
        log.add("[META_INFOS] object_count: 4");
        log.add("[ENDOFTRACE]");

        Trace t = TraceParser.parseTrace(log, new Valuation(), config);
        assert t.getObjectCount() == 4;
        assert t != null;
        System.out.println(t);
    }

    @Test
    public void testDecisionParser() throws IOException, SMTLIBParserException {
        String decl = "(declare-fun __int_0 () Int)";
        String decision = "(assert (= __int_0 50)) // branchCount=2, branchId=0";
        Decision d = TraceParser.parseDecision(decision, decl, config);
        assert d != null;
        System.out.println(d);
    }

    @Test(enabled = true) //@Marvin: This does not work with JConstraints yet.
    public void testTrace() throws IOException, SMTLIBParserException {
        List<String> log = new LinkedList<>();
        log.add("======================== END PATH [BEGIN].");
        log.add("[DECLARE] (declare-fun __int_0 () Int)");
        log.add("[DECISION] (assert (<= 50 (+ 11 __int_0))) // branchCount=2, branchId=1");
        log.add("[ERROR] java.lang.ArrayIndexOutOfBoundsException");
        log.add("======================== END PATH [END].");
        log.add("[META_INFOS] object_count: 4");
        log.add("[ENDOFTRACE]");


        log.add("======================== START PATH [BEGIN].");
        log.add("Concolic Analysis: true");
        log.add("Constructor Summary: false");
        log.add("Taint Analysis: OFF");
        log.add("Seeded Bool Values: []");
        log.add("Seeded Byte Values: []");
        log.add("Seeded Char Values: []");
        log.add("Seeded Short Values: []");
        log.add("Seeded Int Values: []");
        log.add("Seeded Long Values: []");
        log.add("Seeded Float Values: []");
        log.add("Seeded Double Values: []");
        log.add("Seeded String Values: []");
        log.add("Seeded Object Values: []");
        log.add("======================== START PATH [END].");
        log.add("======================== END PATH [BEGIN].");
        log.add("[AUXILIARY] (declare-sort Object 0)");
        log.add("[AUXILIARY] (declare-fun null () Object)");
        log.add("[DECLARE] (declare-fun __object_0 () Object)");
        log.add("[DECLARE] (declare-fun __object_0.cls () String)");
        log.add("[DECISION] (assert (not (and (not (= __object_0 null)) (obj.extends __object_0.cls \"LB;\")))) // branchCount=2, branchId=1");
        log.add("[ABORT] assumption violation");
        log.add("======================== END PATH [END].");
        log.add("[META_INFOS] object_count: 1");
        log.add("[ENDOFTRACE]");

        Trace t = TraceParser.parseTrace(log, new Valuation(), config);
    }

}
