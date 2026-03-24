public class A implements Greeter{
    private int x;
    private int y;
    private Sub sub;

    public A() {}

//    public A(int x) {
//        assert false: "Error in constructor A()";
//    }

    public A(int x,  int y) {
    	this.x = x; 
    	this.y = y; 
    }

    public A(int x, int y,  Sub sub) {
        this.x = x;
        this.y = y;
        this.sub = sub;
    }

    public Sub getSub() {
        return sub;
    }

    public int getX() {
        return this.x;
    }

    public int getY() {
        return this.y;
    }

    public void foo() {
        System.out.println("A correctly executed method foo()");
    }
}
