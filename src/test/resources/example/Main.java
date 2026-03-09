import tools.aqua.concolic.Verifier;
import test.D;

class Main {

  public static void main(String args[]) {
    Object o = Verifier.nondetObject(B.class, null);
    if (!(o instanceof A)) {
      return;
    }
    C b = (C) o;
    b.foo();
    if (b.x > 0) {
      assert false;
    } 
  }

}
