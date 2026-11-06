import tools.aqua.concolic.Verifier;

/* toCharArray and getChars of a symbolic string. */
public class Main {
    public static void main(String[] args) {
        String s = Verifier.nondetString();
        char[] c = s.toCharArray();
        if (c.length == 2 && c[1] == 'x') {
            assert false;
        }
        char[] d = new char[3];
        if (s.length() >= 3) {
            s.getChars(0, 3, d, 0);
            if (d[2] == 'y') {
                throw new IllegalStateException();
            }
        }
    }
}
