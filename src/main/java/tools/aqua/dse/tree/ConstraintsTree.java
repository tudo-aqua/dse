/*
 * Copyright (C) 2015, United States Government, as represented by the
 * Administrator of the National Aeronautics and Space Administration.
 * All rights reserved.
 *
 * Modifications Copyright 2019 TU Dortmund, Falk Howar (@fhowar)
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

import gov.nasa.jpf.constraints.api.ConstraintSolver.Result;
import gov.nasa.jpf.constraints.api.Expression;
import gov.nasa.jpf.constraints.api.SolverContext;
import gov.nasa.jpf.constraints.api.Valuation;
import gov.nasa.jpf.constraints.util.ExpressionUtil;
import tools.aqua.dse.Config;
import tools.aqua.dse.paths.PathResult;
import tools.aqua.dse.paths.PathState;
import tools.aqua.dse.trace.Decision;

import java.util.*;
import java.util.logging.Logger;

public class ConstraintsTree {

  static boolean DEBUG = true;
  private final Logger logger = Logger.getLogger("jdart");
  /** analysis config */
  private final Config config;
  /** constraint solver context for incremental solving */
  private final SolverContext solverCtx;
  /** valuations for replay */
  private final Iterator<Valuation> replayValues;
  /** root of the tree */
  private Node root = LeafNode.open(null, -1);
  /** This is the current node in our EXPLORATION */
  private Node current = root;
  /** required for setting initial valuation after execution */
  private LeafNode initialTarget = null;
  /** This is the node the valuation computed by the constraint solver SHOULD reach */
  private LeafNode currentTarget = (LeafNode) root;
  /** last explored valuation */
  private Valuation currentValues = null;
  /** expected path of current execution */
  private List<Integer> expectedPath = new ArrayList<>();
  /** status flag: execution diverged from expected path */
  private boolean diverged = false;
  /** internal mode switch: explore open nodes */
  private boolean exploreMode;
  /** internal mode switch: replay given valuations */
  private boolean replayMode = true;
  /** use incremental solving */
  private boolean incremental = false;
  /** has the Tree reached the depth limit */
  private boolean isDepthBounded = false;
  /** exploration strategy */
  private ExplorationStrategy strategy = new DFSExplorationStrategy();
  /** termination condition */
  private int termination;
  /** termination flag **/
  private boolean terminate = false;

  public ConstraintsTree(Config config) {
    this.config = config;
    this.solverCtx = config.getSolverContext();
    this.exploreMode = config.getExploreMode();
    this.replayValues = config.getReplayValues();
    this.incremental = config.isIncremental();
    this.termination = config.getTermination();
    this.incremental = config.isIncremental();

    switch (config.getStrategy()) {
      case BFS:
        this.strategy = new BFSExplorationStrategy();
        break;
      case IN_ORDER:
        this.strategy = new InOrderExplorationStrategy();
        break;
      case DFS:
      default:
        // already defaults to DFS
    }

    logger.info("Exploration Strategy: " + strategy.toString());
    logger.info("Incremental solving: " + incremental);
    logger.info("Solver: " + solverCtx.getClass().getSimpleName());

    solverCtx.push();
  }

  public void setExplore(boolean explore) {
    this.exploreMode = explore;
  }


  /**
   * Retrieves the node in the constraints tree that should be reached using the given valuation.
   *
   * @param values the valuation
   * @return the node in the tree that would be reached by the given valuation
   */
  public LeafNode simulate(Valuation values) {
    Node curr = root;
    while (curr.isDecisionNode()) {

      DecisionNode dd = (DecisionNode) curr;
      int branchIdx = -1;
      try {
        branchIdx = dd.evaluate(values);
      } catch (RuntimeException ex) {
        // e.g. due to function with undefined semantics
        return null;
      }

      if (branchIdx < 0) {
        throw new IllegalStateException(
            "Non-complete set of constraints at constraints tree node!");
      }

      curr = dd.getChild(branchIdx);
    }
    return (LeafNode) curr;
  }

  /**
   * Once we diverge, we fail our current target and continue until we hit a leaf that we do not
   * want to explore (anything other than on open, diverged, or d/k
   *
   * @param d
   * @return
   */
  public BranchEffect decision(Decision d) {

    // System.out.println("dec: " + Arrays.toString(decisions) + " : " + branchIdx);

    // 1. at decision node => move down in the tree, check that decision is
    //    expected, and check that decision matches bc decision
    if (current.isDecisionNode()) {

      // move down
      DecisionNode dn = (DecisionNode) current;
      boolean multipleOpen =  dn.missingConstraints() > 1;
      dn.update(d);
      current = dn.getChild(d.getBranchId());

      // if still on track
      if (!replayMode && !diverged) {
        // diverging now?
        // check if still in expected path
        int depth = dn.depth();
        int expectedBranch = expectedPath.get(depth).intValue();
        // FIXME: this could go wrong on true divergence in switch/case
        if ((expectedBranch != d.getBranchId()) && !multipleOpen)  {
          diverged = true;
          // returning unexpected will fail the current target
          // i.e., mark current target as dont_know
          failCurrentTargetDiverged();
          return BranchEffect.UNEXPECTED;
        }

        // check decision
        String error = dn.validateDecision(d);
        if (error != null) {
          // FIXME: maybe we should terminate jdart in this case?
          logger.severe("LIKELY A SEVERE BUG IN DSE:" + error);
          // returning inconclusively will fail the current
          // target and stop executing this path
          failCurrentTargetBuggy("could not validate decision");
          return BranchEffect.BUGGY;
        }
      }
    }
    // 2. at a leaf => expand tree by decision if node is not exhausted
    else {
      LeafNode leaf = (LeafNode) current;
      if (leaf.isExhausted()) {
        // FIXME: maybe we should terminate jdart in this case?
        logger.severe("LIKELY A SEVERE BUG IN DSE: decision at exhausted leaf: + " + leaf);
        // returning inconclusively will fail the current
        // target and stop executing this path
        failCurrentTargetBuggy("decision at exhausted leaf");
        return BranchEffect.BUGGY;
      }

      // expand tree ...
      if (config.maxDepthExceeded(current.depth())) {
        isDepthBounded = true;
        leaf.setComplete(false);
      } else {
        current = expand(leaf, d);
      }
    }

    // already diverged or everything fine or at leaf: return normally
    return BranchEffect.NORMAL;
  }

  private Node expand(LeafNode leaf, Decision d) {
    DecisionNode parent = leaf.parent();
    DecisionNode newInner =
        new DecisionNode(parent, d, leaf.childId(), exploreMode, strategy);
    if (parent == null) {
      root = newInner;
    } else {
      parent.expand(leaf, newInner);
    }
    return newInner.getChild(d.getBranchId());
  }

  private void replaceLeaf(LeafNode oldLeaf, LeafNode newLeaf) {
    if (oldLeaf.parent() != null) {
      oldLeaf.parent().replace(oldLeaf, newLeaf);
    } else {
      root = newLeaf;
    }
  }

  /** @param result */
  public void finish(PathResult result) {
    LeafNode updatedLeaf = null;
    diverged = false;
    // explored to here before ...
    if (((LeafNode) current).isFinal()) {
      return;
    }

    // can happen b/c when exploring switching bytecodes
    if (currentTarget != current) {
      strategy.newOpen(currentTarget);
      //currentTarget = (LeafNode) current;
    }

    switch (result.getState()) {
      case OK:
        updatedLeaf =
            new LeafOK(
                current.parent(),
                current.childId(),
                ((PathResult.OkResult) result).getValuation());
        break;
      case ERROR:
        updatedLeaf =
            new LeafError(
                current.parent(),
                current.childId(),
                ((PathResult.ErrorResult) result).getValuation(),
                ((PathResult.ErrorResult) result).getExceptionClass(),
                ((PathResult.ErrorResult) result).getStackTrace());

        if ((termination & Config.TERMINATE_ON_ASSERTION_VIOLATION) > 0
                && ((PathResult.ErrorResult) result).getExceptionClass().equals("java/lang/AssertionError")) {
          System.out.println("--- terminating DSE after assertion violation");
          terminate = true;
        }

        break;
      case ABORT:
        ((LeafNode) current).setComplete(false);
        updatedLeaf =
            new LeafAbort(
                current.parent(),
                current.childId(),
                ((PathResult.AbortResult) result).getValuation(),
                ((PathResult.AbortResult) result).getReason());
        break;
    }

    if ((termination & Config.TERMINATE_ON_TAINT) > 0
            &&  result.getTaintViolations().size() > 0) {
      System.out.println("--- terminating DSE after tainting violation");
      terminate = true;
    }

    updatedLeaf.setComplete(((LeafNode) current).complete());

    if (current.parent() == null) {
      root = updatedLeaf;
    } else {
      current.parent().replace((LeafNode) current, updatedLeaf);
    }
    current = updatedLeaf;
    if (initialTarget == null) {
      initialTarget = updatedLeaf;
    }
  }

  /** */
  public void failCurrentTargetDontKnow() {
    currentTarget.parent().useUnexploredConstraint(currentTarget.childId());
    LeafNode dk = LeafNode.dontKnow(currentTarget.parent(), currentTarget.childId());
    currentTarget.parent().replace(currentTarget, dk);
    currentTarget = dk;
  }

  public void failCurrentTargetDiverged() {
    currentTarget.parent().useUnexploredConstraint(currentTarget.childId());
    LeafNode div =
        new LeafWithValuation(
            currentTarget.parent(),
            LeafNode.NodeType.DIVERGED,
            currentTarget.childId(),
            currentValues);
    currentTarget.parent().replace(currentTarget, div);
    currentTarget = div;
  }

  public void failCurrentTargetUnsat() {
    currentTarget.parent().useUnexploredConstraint(currentTarget.childId());
    LeafNode unsat = LeafNode.unsat(currentTarget.parent(), currentTarget.childId());
    currentTarget.parent().replace(currentTarget, unsat);
    currentTarget = unsat;
  }

  public void failCurrentTargetBuggy(String cause) {
    LeafNode buggy =
            new LeafBuggy(currentTarget.parent(), currentTarget.childId(), currentValues, cause);
    if (currentTarget.parent() != null) {
      currentTarget.parent().useUnexploredConstraint(currentTarget.childId());
      currentTarget.parent().replace(currentTarget, buggy);
    }
    else {
      root = buggy;
    }
    currentTarget = buggy;
  }

  private void updateContext(Node from, Node to) {
    if (incremental) {
      Node lca = leastCommonAncestor(from, to);
      // System.out.println(lca + " " + from.depth() + " " + to.depth());
      int popDepth = from.depth() - lca.depth();
      if (popDepth > 0) {
        solverCtx.pop(popDepth);
      }

      List<Expression<Boolean>> path = pathConstraint(to, lca);
      for (Expression<Boolean> clause : path) {
        solverCtx.push();
        assertExpression(clause);
      }
    } else {
      solverCtx.pop();
      solverCtx.push();
      List<Expression<Boolean>> path = pathConstraint(to, root);
      //System.out.println("solving: " + Arrays.toString( path.toArray() ));
      solverCtx.add(path);
    }
  }

  private void assertExpression(Expression<Boolean>... expr) {
    try {
      solverCtx.add(expr);
    } catch (RuntimeException ex) {
      // The only consequence of not adding a constraint to the context
      // should be that we may fail to generate models for some paths
      // later on ...
      logger.warning("Failed to add constrtaint " + expr + " due to " + ex.getMessage());
      // ex.printStackTrace();
    }
  }

  private List<Expression<Boolean>> pathConstraint(Node to, Node subTreeRoot) {
    LinkedList<Expression<Boolean>> path = new LinkedList<>();
    Node cur = to;
    while (cur != subTreeRoot) {
      path.addFirst(((DecisionNode) cur.parent()).getConstraint(cur.childId()));
      cur = cur.parent();
    }
    return path;
  }

  private List<Integer> expectedPathTo(Node to) {
    LinkedList<Integer> path = new LinkedList<>();
    Node cur = to;
    while (cur != null) {
      if (cur.parent() != null) {
        path.addFirst(cur.childId());
      }
      cur = cur.parent();
    }
    return path;
  }

  private Node leastCommonAncestor(Node n1, Node n2) {
    Node a1 = null;
    Node a2 = null;
    if (n1.depth() > n2.depth()) {
      a1 = n1;
      a2 = n2;
    } else {
      a1 = n2;
      a2 = n1;
    }
    while (a1.depth() > a2.depth()) {
      a1 = a1.parent();
    }
    while (a1 != a2) {
      a1 = a1.parent();
      a2 = a2.parent();
    }
    return a1;
  }

  /**
   * next concrete valuation
   *
   * @return
   */
  public Valuation findNext() {
    if (terminate) {
      //TODO: close tree somehow?
      return null;
    }

    // mark root for re-execution
    current = root;

    // if we have preset values => use those ...
    if (replayMode) {
      if (replayValues == null || !replayValues.hasNext()) {
        replayMode = false;
      } else {
        currentTarget = null;
        assert this.expectedPath.isEmpty();
        return replayValues.next();
      }
    }

    // else: find next open node to explore
    while (strategy.hasMoreNodes()) {
      LeafNode nextOpen = null;
      while (nextOpen == null) {
        // no more nodes to explore ...
        if (!strategy.hasMoreNodes()) {
          return null;
        }
        nextOpen = strategy.nextOpenNode();

        // check if node is still valid
        if ((nextOpen.parent() == null && root != nextOpen)
            || (nextOpen.parent() != null
                && nextOpen.parent().getChild(nextOpen.childId()) != nextOpen)
            || nextOpen.isFinal()) {
          nextOpen = null;
          continue;
        }
      }

      // update context and current target
      updateContext(
          (currentTarget == null || currentTarget.parent() == null) ? root : currentTarget,
          nextOpen);
      currentTarget = nextOpen;

      // find model
      Valuation val = new Valuation();
      logger.finer("Finding new valuation");
      Result res = solverCtx.solve(val);
      currentValues = val;
      logger.finer("Found: " + res + " : " + val);

      // if node is unsat or dont/know -> next
      // if node is satisfiable -> simulate and execute!
      switch (res) {
        case UNSAT:
          failCurrentTargetUnsat();
          break;
        case ERROR:
          System.out.println("Error SMT result");
        case DONT_KNOW:
          failCurrentTargetDontKnow();
          break;
        case SAT:
          /* discbled b/c jconstraints cant evaluate currently
          LeafNode predictedTarget = simulate(val);
          if (predictedTarget != null && predictedTarget != currentTarget) {
            boolean inconclusive = predictedTarget.isExhausted();
            logger.info("Predicted " + (inconclusive ? "inconclusive " : "") + "divergence");
            if (inconclusive) {
              logger.finer("NOT attempting execution");
              failCurrentTargetBuggy("Failed to simulate");
              break;
            }
          }
           */
          expectedPath = expectedPathTo(currentTarget);
          return val;
        default:
          throw new IllegalStateException("There is an unhandeld result state: " + res);
      }
    }

    // no more nodes
    return null;
  }

  public void setInitialValuation(Valuation initValuation) {
    ((LeafWithValuation) initialTarget).updateValues(initValuation);
  }

  Node root() {
    return root;
  }

  /**
   * does constraints tree explore nodes?
   *
   * @return
   */
  public boolean isExploreMode() {
    return exploreMode;
  }

  public void setExploreMode(boolean b) {
    this.exploreMode = b;
  }

  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("\n");
    root.print(sb, 0);
    if (isDepthBounded) {
      System.out.println("Max depth reached...");
    }
    return sb.toString();
  }

  public enum BranchEffect {
    NORMAL,
    UNEXPECTED,
    BUGGY
  }
}
