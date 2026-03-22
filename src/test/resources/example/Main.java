import  tools.aqua.concolic.Verifier;

class Main {

  public static void main(String args[]) {
      Object o = Verifier.nondetObject(A.class, null);
      Object o2 = Verifier.nondetObject(A.class, null);

      assert (o2 != null);

      if (o instanceof B) {
        B b = (B) o;
        if (b.x > 0) {
          b.foo();
        }
      }
  }

}
