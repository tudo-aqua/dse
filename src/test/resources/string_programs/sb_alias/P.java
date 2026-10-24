public class P {
    public StringBuilder a;
    public StringBuilder b;
    public P(int i) { a = new StringBuilder("x"); b = i > 0 ? a : new StringBuilder("x"); }  // shared iff i > 0
}
