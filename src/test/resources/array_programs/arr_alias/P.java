public class P {
    public int[] a;
    public int[] b;
    public P(int i) { a = new int[3]; b = i > 0 ? a : new int[3]; }  // shared iff i > 0
}
