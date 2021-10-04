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

import java.util.LinkedList;

class InOrderExplorationStrategy implements ExplorationStrategy {

    private LinkedList<LeafNode> nodes = new LinkedList<>();

    @Override
    public LeafNode nextOpenNode() {
        return nodes.poll();
    }

    @Override
    public void newOpen(LeafNode n) {
        nodes.offer(n);
    }

    @Override
    public boolean hasMoreNodes() {
        return !nodes.isEmpty();
    }
}
