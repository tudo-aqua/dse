package tools.aqua.dse.witness.svcomp;

/*
 * required:
 * - file_name
 * - line
 * properties:
 *   file_name:
 *     type: string
 *   line:
 *     type: integer
 *     minimum: 1
 *   column:
 *     type: integer
 *     minimum: 1
 *   function:
 *     type: string
 */

public class WitnessLocation {
    public String fileName;
    public int line;
    public int column;
    public String function;

    public WitnessLocation(String fileName, int line) {
        this.fileName = fileName;
        this.line = line;
    }
    public WitnessLocation(String fileName, int line, String function) {
        this.fileName = fileName;
        this.line = line;
        this.function = function;
    }
}
