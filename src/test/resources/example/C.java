public class C extends B {

  private final A a;

  public C(int x) {
    super(x);
    a = null;
    if (x == 1) assert false;
  }

  public C(A a) {
    super(0);
    this.a = a;
  }

}
