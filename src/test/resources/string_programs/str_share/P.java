public class P {
    public String a;
    public String b;
    public P(int i) { a = new String("s"); b = i > 0 ? a : new String("s"); }   // a and b alias iff i > 0
}
