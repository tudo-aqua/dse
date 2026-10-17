public class P {
    public String a;
    public String b;
    public P(int i) { a = new String("s"); b = new String(i > 0 ? "s" : "t"); }  // distinct objects, equal iff i > 0
}
