public class Ring {
    public Ring next;
    public Ring() { next = new Ring(this, 0); }        // cycle of length 2
    public Ring(Ring n, int unused) { next = n; }
}
