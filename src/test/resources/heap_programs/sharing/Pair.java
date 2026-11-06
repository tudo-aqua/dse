public class Pair {
    public Object a;
    public Object b;
    public Pair(Object x) { a = x; b = x; }            // a and b alias
    public Pair(Object x, Object y) { a = x; b = y; }
}
