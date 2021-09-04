package tools.aqua.dse.tree;

interface ExplorationStrategy {

    LeafNode nextOpenNode();

    void newOpen(LeafNode n);

    boolean hasMoreNodes();
}
