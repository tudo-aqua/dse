public class B extends A {
  int x = 0;

  public B() {
    assert false;
  }

  public B(int x) {
    this.x = x;
  }

  public void foo() {}
}
