public class BoxPair {
    public Box a;
    public Box b;
    public BoxPair(Box x) { a = x; b = x; }            // a and b alias
    public BoxPair(Box x, Box y) { a = x; b = y; }
}
