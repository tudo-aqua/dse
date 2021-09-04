/*
 * Copyright (C) 2015, United States Government, as represented by the
 * Administrator of the National Aeronautics and Space Administration.
 * All rights reserved.
 *
 * The PSYCO: A Predicate-based Symbolic Compositional Reasoning environment
 * platform is licensed under the Apache License, Version 2.0 (the "License"); you
 * may not use this file except in compliance with the License. You may obtain a
 * copy of the License at http://www.apache.org/licenses/LICENSE-2.0.
 *
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package tools.aqua.dse.tree;

import java.util.*;

/**
 * constraints tree implementation.
 */
public class ConstraintsTreeAnalysis {

    private final ConstraintsTree constraintsTree;

    public ConstraintsTreeAnalysis(ConstraintsTree tree) {
        this.constraintsTree = tree;
    }

    /**
     * pretty print
     *
     * @return
     */
    @Override
    public String toString() {
        return constraintsTree.toString();
    }

    /* **************************************************************************
     *
     * data retrieval
     *
     */
    public Collection<LeafNode> getOpenLeafs() {
        return getNodesMatchingStates(EnumSet.of(LeafNode.NodeType.OPEN));
    }

    public Collection<LeafNode> getOkLeafs() {
        return getNodesMatchingStates(EnumSet.of(LeafNode.NodeType.OK));
    }

    public Collection<LeafNode> getErrorLeafs() {
        return getNodesMatchingStates(EnumSet.of(LeafNode.NodeType.ERROR));
    }

    public Collection<LeafNode> getDontKnowLeafs() {
        return getNodesMatchingStates(EnumSet.of(LeafNode.NodeType.DONT_KNOW));
    }

    public Collection<LeafNode> getSkippedLeafs() {
        return getNodesMatchingStates(EnumSet.of(LeafNode.NodeType.SKIPPED));
    }

    public Collection<LeafNode> getBuggyLeafs() {
        return getNodesMatchingStates(EnumSet.of(LeafNode.NodeType.BUGGY));
    }

    public Collection<LeafNode> getUnsatLeafs() {
        return getNodesMatchingStates(EnumSet.of(LeafNode.NodeType.UNSAT));
    }

    public Collection<LeafNode> getDivergedLeafs() {
        return getNodesMatchingStates(EnumSet.of(LeafNode.NodeType.DIVERGED));
    }

    private Collection<LeafNode> getNodesMatchingStates(EnumSet<LeafNode.NodeType> states) {

        Collection<LeafNode> matching = new LinkedList<>();
        LinkedList<Node> worklist = new LinkedList<>();
        worklist.offer(constraintsTree.root());

        while (!worklist.isEmpty()) {
            Node n = worklist.poll();

            if (!n.isDecisionNode()) {
                LeafNode leaf = (LeafNode) n;
                if (states.contains(((LeafNode) n).nodeType())) {
                    matching.add(leaf);
                }
            } else {
                for (Node c : ((DecisionNode) n).children()) {
                    worklist.offer(c);
                }
            }
        }
        return matching;
    }

}
