public class ComplexClass {
    private int n;
    private A a;

    public ComplexClass(int n, A a) {
        this.n = n;
        this.a = a;
    }

    public int getN() {
        return this.n;
    }

    public A getA() {
        return a;
    }

    @Override
    public String toString() {
        return "ComplexClass{" +
                "n='" + this.n + '\'' +
                ", a=" + this.a +
                '}';
    }
}