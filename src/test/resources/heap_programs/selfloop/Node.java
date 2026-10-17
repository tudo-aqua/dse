public class Node {
    public Node next;
    public Node() { next = this; }           // heap loop
    public Node(Node n) { next = n; }
}
