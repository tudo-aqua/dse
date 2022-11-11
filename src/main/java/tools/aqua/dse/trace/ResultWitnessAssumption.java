package tools.aqua.dse.trace;

public class ResultWitnessAssumption {

    private final String caller;
    private final int line;
    private final String callee;
    private final String result;

    public ResultWitnessAssumption(String caller, int line, String callee, String result) {
        this.caller = caller;
        this.line = line;
        this.callee = callee;
        this.result = result;
    }

    public String getCaller() {
        return caller;
    }

    public int getLine() {
        return line;
    }

    public String getCallee() {
        return callee;
    }

    public String getResult() {
        return result;
    }
}
