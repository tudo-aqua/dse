import tools.aqua.concolic.Verifier;

/* String.split with a literal separator: the number of parts and their contents depend on the input. */
public class Main {
    public static void main(String[] args) {
        String s = Verifier.nondetString();
        String[] parts = s.split(",");
        if (parts.length == 3 && parts[1].equals("b")) {
            assert false;
        }
        if (s.length() > 0 && parts.length == 0) {
            throw new IllegalStateException();   // only separators: trailing empty strings are removed
        }
    }
}
