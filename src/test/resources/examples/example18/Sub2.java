public class Sub2 extends Sub{
    private int z;

    public Sub2(int z) {
        this.z = z;
        assert false: "Error in constructor Sub2(int z)";
    }

    public Sub2() {
    }

    public int getZ() {
        return z;
    }

    @Override
    public void bar() {
        assert false: "Error in Sub2.bar()";
    }


}
