public class RecursiveIntList {
    private final int value;
    private final RecursiveIntList next;

    public RecursiveIntList(int value, RecursiveIntList next) {
        this.value = value;
        this.next = next;
    }

    public boolean isEmpty() {
        return this.next == null;
    }

    public int getValue() {
        return value;
    }

    public RecursiveIntList getNext() {
        return next;
    }

    public RecursiveIntList add(int value) {
        return new RecursiveIntList(value, this);
    }

    public int size() {
        return (next == null) ? 1 : 1 + next.size();
    }

    public boolean contains(int value) {
        if (this.value == value) {
            return true;
        }
        return next != null && next.contains(value);
    }
    
    @Override
    public String toString() {
        return value + (next != null ? " -> " + next.toString() : " -> null");
    }
}
