//*****************************************************************************/
//* Copyright (C) 2014                                                        */
//* ALEXEY MERKULOV  steelart (dot) alex (at) gmail (dot) com                 */
//* License terms: GNU General Public License Version 2                       */
//*                or any later version                                       */
//*****************************************************************************/
package mmj.pa;

import java.util.*;

import mmj.lang.*;
import mmj.verify.VerifyProofs;

/**
 * This contains information for possible automatic transformations.
 */
public class ProofTransformations {
    private boolean isInit = false;
    /** The list of equivalence operators */
    private Map<Stmt, Assrt> eqOperators;

    /** The list of commutative operators */
    private Map<Stmt, Assrt> comOp;

    /** The list of statements with possible variable replace */
    private Map<Stmt, Assrt[]> replaceOp;

    /** The list of associative operators */
    private Set<Stmt> assocOp;

    /** Empty default constructor */
    public ProofTransformations() {}

    // ----------------------------

    public void prepareAutomaticTransformations(final List<Assrt> assrtList,
        final Messages messages)
    {
        isInit = true;

        eqOperators = new HashMap<Stmt, Assrt>();
        for (final Assrt assrt : assrtList)
            findEquivalenceRules(assrt, messages);

        comOp = new HashMap<Stmt, Assrt>();
        for (final Assrt assrt : assrtList)
            findCommutativeRules(assrt, messages);

        assocOp = new HashSet<Stmt>();
        for (final Assrt assrt : assrtList)
            findAssociativeRules(assrt, messages);

        replaceOp = new HashMap<Stmt, Assrt[]>();
        for (final Assrt assrt : assrtList)
            findReplaceRules(assrt, messages);
    }

    private void findEquivalenceRules(final Assrt assrt, final Messages messages)
    {
        // Try to find equivalence rules, like A = B <=> B = A
        // TODO: find implication rules also
        final VarHyp[] varHypArray = assrt.getMandVarHypArray();
        final LogHyp[] logHyps = assrt.getLogHypArray();
        final ParseTree assrtTree = assrt.getExprParseTree();

        if (logHyps.length != 1)
            return;

        final ParseTree hypTree = logHyps[0].getExprParseTree();

        if (varHypArray.length != 2)
            return;

        if (hypTree.getMaxDepth() != 2)
            return;

        if (assrtTree.getMaxDepth() != 2)
            return;

        if (assrtTree.getRoot().getChild().length != 2)
            return;

        if (hypTree.getRoot().getStmt() != assrtTree.getRoot().getStmt())
            return;

        if (hypTree.getRoot().getChild()[0].getStmt() != assrtTree.getRoot()
            .getChild()[1].getStmt())
            return;

        if (hypTree.getRoot().getChild()[1].getStmt() != assrtTree.getRoot()
            .getChild()[0].getStmt())
            return;

        messages.accumInfoMessage("I-DBG Equivalence assrts: %s",
            assrt.toString());
        eqOperators.put(assrtTree.getRoot().getStmt(), assrt);
    }

    private void findCommutativeRules(final Assrt assrt, final Messages messages)
    {
        // TODO: adds the support of assrts like addcomi
        // Find commutative rules, like A + B = B + A

        final VarHyp[] varHypArray = assrt.getMandVarHypArray();
        final ParseTree assrtTree = assrt.getExprParseTree();

        // if (assrt.toString().equals("addcomi"))
        // assrt.toString();

        final LogHyp[] logHyps = assrt.getLogHypArray();
        if (logHyps.length != 0)
            return;

        if (varHypArray.length != 2)
            return;

        if (varHypArray.length != 2)
            return;

        if (assrtTree.getMaxDepth() != 3)
            return;

        if (!eqOperators.containsKey(assrtTree.getRoot().getStmt()))
            return;

        final ParseNode[] subTrees = assrtTree.getRoot().getChild();

        // it is the equivalence rule
        assert subTrees.length == 2;

        if (subTrees[0].getStmt() != subTrees[1].getStmt())
            return;

        if (subTrees[0].getChild().length != 2)
            return;

        if (subTrees[0].getChild()[0].getStmt() != subTrees[1].getChild()[1]
            .getStmt())
            return;

        if (subTrees[0].getChild()[1].getStmt() != subTrees[1].getChild()[0]
            .getStmt())
            return;

        messages.accumInfoMessage("I-DBG commutative assrts: %s: %s",
            assrt.toString(), assrt.getFormula().toString());
        comOp.put(subTrees[0].getStmt(), assrt);
    }

    private void findReplaceRules(final Assrt assrt, final Messages messages) {

        assrt.getMandVarHypArray();
        final LogHyp[] logHyps = assrt.getLogHypArray();
        final ParseTree assrtTree = assrt.getExprParseTree();

        // TODO: find rules in the form of implication!
        // TODO: logHyps could contains other hypotheses
        if (logHyps.length != 1)
            return;

        // Maybe depth restriction could be weaken
        if (assrtTree.getMaxDepth() != 3)
            return;

        if (eqOperators.get(assrtTree.getRoot().getStmt()) == null)
            return;

        final LogHyp testHyp = logHyps[0];

        final ParseTree hypTree = testHyp.getExprParseTree();

        if (eqOperators.get(hypTree.getRoot().getStmt()) == null)
            return;

        final ParseNode[] hypSubTrees = hypTree.getRoot().getChild();

        assert hypSubTrees.length == 2 : "It should be the equivalence rule!";

        if (!isVarNode(hypSubTrees[0]) || !isVarNode(hypSubTrees[1]))
            return;

        final ParseNode[] subTrees = assrtTree.getRoot().getChild();

        assert subTrees.length == 2 : "It should be the equivalence rule!";

        if (subTrees[0].getStmt() != subTrees[1].getStmt())
            return;

        final Stmt stmt = subTrees[0].getStmt();

        final ParseNode[] leftChild = subTrees[0].getChild();
        final ParseNode[] rightChild = subTrees[1].getChild();

        // Fast compare, change if the depth of this assrt statement tree
        // could be more then 3
        int replPos = -1;
        replaceCheck: for (int i = 0; i < leftChild.length; i++)
            if (leftChild[i].getStmt() != rightChild[i].getStmt()) {
                // Another place for replace? It is strange!
                if (replPos != -1)
                    return;

                // We found the replace
                replPos = i;

                // Check that it is actually the swap of two variables
                for (int k = 0; k < 2; k++) {
                    final int m = (k + 1) % 2; // the other index
                    if (leftChild[i].getStmt() == hypSubTrees[k].getStmt()
                        && rightChild[i].getStmt() == hypSubTrees[m].getStmt())
                        continue replaceCheck;
                }

                return;
            }

        Assrt[] repl = replaceOp.get(stmt);

        if (repl == null) {
            repl = new Assrt[subTrees[0].getChild().length];
            replaceOp.put(stmt, repl);
        }

        // it is the first such assrt;
        if (repl[replPos] != null)
            return;

        repl[replPos] = assrt;

        messages.accumInfoMessage("I-DBG Replace assrts: %s: %s",
            assrt.toString(), assrt.getFormula().toString());
    }

    private void findAssociativeRules(final Assrt assrt, final Messages messages)
    {
        // Find now associative rules, like (A + B) + C = A + (B + C)
        final VarHyp[] varHypArray = assrt.getMandVarHypArray();
        // final LogHyp[] logHyps = assrt.getLogHypArray();
        final ParseTree assrtTree = assrt.getExprParseTree();

        // if (assrt.toString().equals("addcomi"))
        // assrt.toString();

        // TODO: adds the support of assrts like addcomi

        if (varHypArray.length != 3)
            return;

        if (assrtTree.getMaxDepth() != 4)
            return;

        if (!eqOperators.containsKey(assrtTree.getRoot().getStmt()))
            return;

        final ParseNode[] subTrees = assrtTree.getRoot().getChild();

        // it is the equivalence rule
        assert subTrees.length == 2;

        if (subTrees[0].getStmt() != subTrees[1].getStmt())
            return;

        final Stmt f = subTrees[0].getStmt();
        // we need to find one of the 2 patterns:
        // 1) f(a, f(b, c)) = f(f(a, b), c)
        // 2) f(f(a, b), c) = f(a, f(b, c))
        for (int i = 0; i < 2; i++) {
            final int j = (i + 1) % 2;
            final ParseNode[] leftChild = subTrees[0].getChild();
            final ParseNode[] rightChild = subTrees[1].getChild();
            if (leftChild[i].getStmt() != f)
                continue;

            if (rightChild[j].getStmt() != f)
                continue;

            if (!isVarNode(leftChild[j]))
                continue;

            if (leftChild[j].getStmt() != rightChild[j].getChild()[j].getStmt())
                continue;

            if (leftChild[i].getChild()[i].getStmt() != rightChild[i].getStmt())
                continue;

            if (leftChild[i].getChild()[j].getStmt() != rightChild[j]
                .getChild()[i].getStmt())
                continue;

            messages.accumInfoMessage("I-DBG associative assrts: %s: %s",
                assrt.toString(), assrt.getFormula().toString());
            assocOp.add(assrtTree.getRoot().getStmt());
            return;
        }
    }

    // ----------------------------------------------

    public static class Transformation {
        final ParseNode canonResult;
        final ParseNode equivalence;
        final Assrt assrt;
        Transformation next;

        public Transformation(final ParseNode canonResult,
            final ParseNode equivalence, final Assrt assrt)
        {
            this.canonResult = canonResult;
            this.equivalence = equivalence;
            this.assrt = assrt;
            next = null;
        }

        public void addToTheTail(final Transformation second) {
            // TODO: optimize it!
            Transformation last = this;
            while (last.next != null)
                last = last.next;

            last.next = second;
        }
    }

    // Concatenates transformations
    private static Transformation concatTrs(final Transformation first,
        final Transformation second)
    {
        assert first != null || second != null;
        if (first != null) {
            first.addToTheTail(second);
            return first;
        }
        return second;
    }

    public Transformation getCanonicalForm(final ParseNode node) {
        final Stmt stmt = node.getStmt();

        final Assrt[] replAsserts = replaceOp.get(stmt);
        final Assrt comAssert = comOp.get(stmt);

        final boolean subTreesCouldBeRepl = replAsserts != null;
        final boolean comOper = comAssert != null;

        if (!comOper && !subTreesCouldBeRepl)
            return null; // We could do nothing with this node!

        Transformation resTr = null;

        final int length = node.getChild().length;
        final ParseNode[] origChildren = node.getChild();
        ParseNode resNode = node;

        if (subTreesCouldBeRepl)
            // Now we could reconstruct subtrees!
            for (int i = 0; i < length; i++) {
                final Assrt replAssert = replAsserts[i];
                if (replAssert == null)
                    // We can't transform this sub-tree
                    continue;

                // Get sub-node transformation:
                final Transformation subTr = getCanonicalForm(origChildren[i]);

                if (subTr == null)
                    // We should not do any transformations
                    continue;

                final ParseNode prevVersion = resNode;
                resNode = prevVersion.cloneWithoutChildren();

                // Fill the next child
                resNode.getChild()[i] = subTr.canonResult;

                // Construct the next step of this node transformation:
                final Stmt eqStmt = replAssert.getExprParseTree().getRoot()
                    .getStmt();
                final ParseNode eqRoot = new ParseNode(eqStmt);
                final ParseNode[] eqChildren = {prevVersion, resNode};
                eqRoot.setChild(eqChildren);
                final Transformation eqTr = new Transformation(resNode, eqRoot,
                    replAssert);

                // Add subtree transformation:
                final Transformation trStep = concatTrs(eqTr, subTr);

                // Update the full transformation:
                resTr = concatTrs(trStep, resTr);

            }

        if (comOper) {
            // This node is the commutative operation node
            assert length == 2;

            if (compareNodes(resNode.getChild()[0], resNode.getChild()[1]) > 0)
            {
                final ParseNode prevVersion = resNode;
                resNode = prevVersion.cloneWithoutChildren();

                // Swap it!
                final ParseNode tmp = resNode.getChild()[0];
                resNode.getChild()[0] = resNode.getChild()[1];
                resNode.getChild()[1] = tmp;

                // Construct the next step of this node transformation:
                final Stmt eqStmt = comAssert.getExprParseTree().getRoot()
                    .getStmt();
                final ParseNode eqRoot = new ParseNode(eqStmt);
                final ParseNode[] eqChildren = {prevVersion, resNode};
                eqRoot.setChild(eqChildren);
                final Transformation eqTr = new Transformation(resNode, eqRoot,
                    comAssert);
                resTr = concatTrs(eqTr, resTr);
            }
        }
        return resTr;
    }

    /**
     * @param first The one operand
     * @param second The other operand
     * @return -1(less), 0(equal),1(greater)
     */
    private static int compareNodes(final ParseNode first,
        final ParseNode second)
    {
        if (first.getStmt() == second.getStmt()) {
            final int len = first.getChild().length;
            for (int i = 0; i < len; i++) {
                final int res = compareNodes(first.getChild()[i],
                    second.getChild()[i]);
                if (res != 0)
                    return res;
            }

            return 0;
        }
        return first.getStmt().getSeq() < second.getStmt().getSeq() ? -1 : 1;
    }

    // -------------

    private Formula showCanonicalForm(final VerifyProofs verifyProofs,
        final ProofStepStmt proofStmt)
    {
        return getFormula(verifyProofs, proofStmt.getCanonicalForm());
    }

    private Formula getFormula(final VerifyProofs verifyProofs,
        final ParseNode node)
    {
        final ParseTree tree = new ParseTree(node);
        final Formula generatedFormula = verifyProofs.convertRPNToFormula(
            tree.convertToRPN(), "tree"); // TODO: use constant
        return generatedFormula;
    }

    private DerivationStep printTransformations(final Transformation tr,
        final VerifyProofs verifyProofs, final Messages messages,
        final ProofWorksheet proofWorksheet)
    {
        if (tr == null)
            return null;

        final DerivationStep prev = printTransformations(tr.next, verifyProofs,
            messages, proofWorksheet);

        final ParseTree tree = new ParseTree(tr.equivalence);
        final Formula generatedFormula = verifyProofs.convertRPNToFormula(
            tree.convertToRPN(), "tree"); // TODO: use constant

        final ProofStepStmt[] hyps;
        String[] steps;
        if (tr.assrt.getLogHypArray().length == 1) {
            final ProofStepStmt[] hypss = {prev};
            hyps = hypss;
            final String[] stepss = {prev.getStep()};
            steps = stepss;
        }
        else {
            hyps = new ProofStepStmt[0];
            steps = new String[0];
        }

        final DerivationStep d = proofWorksheet.generateDerivStep(hyps, steps,
            tr.assrt.getLabel(), generatedFormula, tree,
            Collections.<WorkVar> emptyList());

        messages.accumInfoMessage("I-DBG Transformation (%s): %s", tr.assrt, d);
        return d;
    }
    // ---------------------

    public void tryToFindTransformations(final ProofWorksheet proofWorksheet,
        final DerivationStep derivStep, final VerifyProofs verifyProofs,
        final Messages messages)
    {
        if (!isInit)
            return;

        final Transformation dsCanonicalForm = getCanonicalForm(derivStep.formulaParseTree
            .getRoot());
        derivStep.setCanonicalTransformation(dsCanonicalForm);

        messages.accumInfoMessage("I-DBG Step %s has canonical form: %s",
            derivStep, showCanonicalForm(verifyProofs, derivStep));

        printTransformations(dsCanonicalForm, verifyProofs, messages,
            proofWorksheet);

        for (final ProofWorkStmt proofWorkStmtObject : proofWorksheet
            .getProofWorkStmtList())
        {

            if (proofWorkStmtObject == derivStep)
                break;

            if (!(proofWorkStmtObject instanceof ProofStepStmt))
                continue;

            final ProofStepStmt candidate = (ProofStepStmt)proofWorkStmtObject;

            if (candidate.getCanonicalTransformation() == null)
                if (!candidate.isSameCanonicalForm()) {
                    final Transformation tr = getCanonicalForm(candidate.formulaParseTree
                        .getRoot());
                    candidate.setCanonicalTransformation(tr);

                    messages.accumInfoMessage(
                        "I-DBG Step %s has canonical form: %s", candidate,
                        showCanonicalForm(verifyProofs, candidate));
                }

            if (derivStep.getCanonicalForm().isDeepDup(
                candidate.getCanonicalForm()))
                messages.accumInfoMessage(
                    "I-DBG found canonical forms correspondance: %s and %s",
                    candidate.toString(), derivStep.toString());
        }

    }
    // ------------Additional functions--------------

    private static boolean isVarNode(final ParseNode node) {
        return isVarStmt(node.getStmt());
    }

    private static boolean isVarStmt(final Stmt stmt) {
        return stmt instanceof VarHyp;
    }
}