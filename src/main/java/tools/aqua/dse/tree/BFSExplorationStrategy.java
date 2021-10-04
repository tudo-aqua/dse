/*
 * Copyright (C) 2021, Automated Quality Assurance Group,
 * TU Dortmund University, Germany. All rights reserved.
 *
 * DSE (dynamic symbolic execution) is licensed under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0.
 *
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package tools.aqua.dse.tree;

import java.util.Comparator;
import java.util.PriorityQueue;

class BFSExplorationStrategy implements ExplorationStrategy {

    private PriorityQueue<LeafNode> queue = new PriorityQueue<>(100, new Comparator<LeafNode>() {
        // Note: this comparator imposes orderings that are inconsistent with equals.
        @Override
        public int compare(LeafNode o1, LeafNode o2) {
            return o1.depth() - o2.depth();
        }
    });

    @Override
    public LeafNode nextOpenNode() {
        return queue.poll();
    }

    @Override
    public void newOpen(LeafNode n) {
        queue.offer(n);
    }

    @Override
    public boolean hasMoreNodes() {
        return !queue.isEmpty();
    }
}
