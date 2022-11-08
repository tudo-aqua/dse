package tools.aqua.dse.iflow;

import gov.nasa.jpf.constraints.api.ConstraintSolver;
import gov.nasa.jpf.constraints.api.Expression;
import gov.nasa.jpf.constraints.api.SolverContext;
import gov.nasa.jpf.constraints.api.Variable;

import gov.nasa.jpf.constraints.expressions.LogicalOperator;
import gov.nasa.jpf.constraints.expressions.PropositionalCompound;
import gov.nasa.jpf.constraints.types.BuiltinTypes;
import gov.nasa.jpf.constraints.util.ExpressionUtil;
import tools.aqua.dse.Config;

import java.util.*;

public class InformationFlowAnalysis {

    private final SolverContext solverCtx;

    private final Map<String, Set<String>> flows = new HashMap<>();

    private HashMap<String, Set<String>> taintchecks = new HashMap<>();

    public InformationFlowAnalysis(Config config) {
        this.solverCtx = config.getSolverContext();
    }

    public void addFlow(String flowInfo) {
        //System.out.println("-- " + flowInfo);
        if (flowInfo.startsWith("checkFor")) {
            addCheck(flowInfo);
        }
        else {
            addIFlow(flowInfo);
        }
    }

    private void addCheck(String checkInfo) {
        String[] condition = checkInfo.trim().split("on value tainted by");
        String var = condition[0].replace("checkFor=", "").trim();

        Set<String> varTaint = taintchecks.get(var);
        if (varTaint == null) {
            varTaint = new HashSet<>();
            taintchecks.put(var, varTaint);
        }

        if (condition.length > 1) {
            String[] taint = condition[1].trim().split(",");
            for (String t : taint) {
                varTaint.add(t.trim());
            }
        }
    }

    private void addIFlow(String flowInfo) {
        String[] condition = flowInfo.trim().split("tainted by");
        String var = condition[0].replace("name=", "").trim();

        Set<String> varTaint = flows.get(var);
        if (varTaint == null) {
            varTaint = new HashSet<>();
            flows.put(var, varTaint);
        }

        if (condition.length > 1) {
            String[] taint = condition[1].trim().split(",");
            for (String t : taint) {
                varTaint.add(t.trim());
            }
        }
    }

    public void listFlows() {
        for (String v : flows.keySet()) {
            System.out.println("FLOW: " + v + " from " + Arrays.toString(flows.get(v).toArray()));
        }
        for (String v : taintchecks.keySet()) {
            System.out.println("CHECK: " + v + " from " + Arrays.toString(taintchecks.get(v).toArray()));
        }
    }

    public void runChecks() {
        Map<String, Variable> vars = new HashMap<>();
        solverCtx.push();
        generate(vars);
        solverCtx.push();

        for (String v : taintchecks.keySet()) {
            solverCtx.add(new PropositionalCompound(getOrCreate(v, vars), LogicalOperator.EQUIV, ExpressionUtil.TRUE));

            for (String vOther : vars.keySet()) {
                if (!vOther.startsWith("if") && !vOther.equals(v)) {
                    solverCtx.add(new PropositionalCompound(
                            getOrCreate(vOther, vars), LogicalOperator.EQUIV, ExpressionUtil.FALSE));
                }
            }
            boolean violation = (solverCtx.isSatisfiable() == ConstraintSolver.Result.SAT);
            System.out.println(violation ? "[TAINT VIOLATION] INFORMATION FLOW/TAINT for " + v + " " + "discovered" : "No information flow found");
            solverCtx.pop();
        }
    }

    private void generate(Map<String, Variable> vars) {

        for (String v : flows.keySet()) {
            Set<String> taint = flows.get(v);
            Variable key = getOrCreate(v, vars);

            Expression[] taintVars = new Expression[taint.size()];
            int i=0;
            for (String t : taint) {
                taintVars[i++] = getOrCreate(t, vars);
            }

            Expression flow = new PropositionalCompound(key, LogicalOperator.EQUIV, ExpressionUtil.or(taintVars));
            solverCtx.add(flow);
        }

        for (Set<String> check : taintchecks.values()) {
            Expression[] checkVars = new Expression[check.size()];
            int i=0;
            for (String t : check) {
                checkVars[i++] = getOrCreate(t, vars);
            }

            solverCtx.add(ExpressionUtil.or(checkVars));
        }

    }

    private Variable getOrCreate(String name, Map<String, Variable> vars) {
        Variable v = vars.get(name);
        if (v == null) {
            v = new Variable(BuiltinTypes.BOOL, name);
        }
        return v;
    }

}
