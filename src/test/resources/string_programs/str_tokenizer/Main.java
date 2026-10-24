import tools.aqua.concolic.Verifier;
import java.util.StringTokenizer;

/* StringTokenizer (JDK code over charAt, indexOf, and substring of the symbolic string). */
public class Main {
    public static void main(String[] args) {
        String s = Verifier.nondetString();
        StringTokenizer tokens = new StringTokenizer(s, ",");
        int n = tokens.countTokens();
        if (n == 2 && tokens.nextToken().equals("a")) {
            assert false;
        }
        if (n > 2) {
            throw new IllegalStateException();
        }
    }
}
