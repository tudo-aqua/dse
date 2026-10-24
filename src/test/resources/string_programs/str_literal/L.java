public class L {
    public String s;
    public L(int i) { s = i > 0 ? "lit" : new String("lit"); }   // the literal is interned: shared by all L(i > 0)
}
