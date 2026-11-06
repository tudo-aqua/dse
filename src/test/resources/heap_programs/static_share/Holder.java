public class Holder {
    public static final Object SHARED = new Object();
    public Object o;
    public Holder() { o = SHARED; }                   // all such holders share one object
    public Holder(Object x) { o = x; }
}
