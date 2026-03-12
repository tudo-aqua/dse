import tools.aqua.concolic.Verifier;

class SummaryMain {

    public static void main(String args[]) {
        Object o = Verifier.nondetObject();
    }
}