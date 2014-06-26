package mmj.transforms;

import java.util.*;

import mmj.lang.*;
import mmj.pa.ProofStepStmt;

/**
 * The information about commutative operations.
 * <p>
 * Note: the current implementation contains some stub implementations!
 */
public class CommutativeInfo extends DBInfo {
    /** The information about equivalence rules */
    private final EquivalenceInfo eqInfo;

    final ClosureInfo clInfo;

    /**
     * The list of commutative operators: ( A + B ) = ( B + A )
     * <p>
     * It is a map: Statement ( ( A F B ) in the example) -> map : constant
     * elements ( + in the example) -> map : possible properties ( _ e. CC in
     * the example) -> assert. There could be many properties ( {" _ e. CC" ,
     * "_ e. RR" } for example ).
     */
    protected Map<Stmt, Map<ConstSubst, Map<PropertyTemplate, Assrt>>> comOp;

    // ------------------------------------------------------------------------
    // ------------------------Initialization----------------------------------
    // ------------------------------------------------------------------------

    public CommutativeInfo(final EquivalenceInfo eqInfo,
        final ClosureInfo clInfo, final List<Assrt> assrtList,
        final TrOutput output, final boolean dbg)
    {
        super(output, dbg);
        this.eqInfo = eqInfo;
        this.clInfo = clInfo;

        comOp = new HashMap<Stmt, Map<ConstSubst, Map<PropertyTemplate, Assrt>>>();
        for (final Assrt assrt : assrtList)
            findCommutativeRules(assrt);
    }

    /**
     * Filters commutative rules, like A + B = B + A
     * 
     * @param assrt the candidate
     */
    protected void findCommutativeRules(final Assrt assrt) {
        // Debug statements: prcom, addcomi
        final VarHyp[] varHypArray = assrt.getMandVarHypArray();
        final ParseTree assrtTree = assrt.getExprParseTree();

        if (assrt.getLabel().toString().equals("addcomi"))
            assrt.toString();

        final PropertyTemplate template = ClosureInfo
            .createTemplateFromHyp(assrt);

        if (template == null)
            return;

        if (varHypArray.length != 2)
            return;

        if (!eqInfo.isEquivalence(assrtTree.getRoot().getStmt()))
            return;

        final ParseNode[] subTrees = assrtTree.getRoot().getChild();

        // it is the equivalence rule
        assert subTrees.length == 2;

        if (subTrees[0].getStmt() != subTrees[1].getStmt())
            return;

        final Stmt stmt = subTrees[0].getStmt();

        final ConstSubst constSubst = ConstSubst.createFromNode(subTrees[0]);

        final int[] varPlace = constSubst.getVarPlace();

        // the statement contains more that 2 variables
        if (varPlace.length != 2)
            return;

        if (!constSubst.isTheSameConstMap(subTrees[1]))
            return;

        // It is unnecessary:
        // if (!template.isEmpty())
        // if (clInfo.getClosureAssert(stmt, constSubst, template) == null)
        // return;

        final ParseNode[] leftChildren = subTrees[0].getChild();
        final ParseNode[] rightChildren = subTrees[1].getChild();

        final int k0 = varPlace[0];
        final int k1 = varPlace[1];

        if (leftChildren[k0].getStmt() != rightChildren[k1].getStmt())
            return;

        if (leftChildren[k1].getStmt() != rightChildren[k0].getStmt())
            return;

        Map<ConstSubst, Map<PropertyTemplate, Assrt>> constSubstMap = comOp
            .get(stmt);

        if (constSubstMap == null) {
            // TODO: linked hash map stub!
            constSubstMap = new LinkedHashMap<ConstSubst, Map<PropertyTemplate, Assrt>>();
            comOp.put(stmt, constSubstMap);
        }

        Map<PropertyTemplate, Assrt> propertyMap = constSubstMap
            .get(constSubst);
        if (propertyMap == null) {
            // TODO: linked hash map stub!
            propertyMap = new LinkedHashMap<PropertyTemplate, Assrt>();
            constSubstMap.put(constSubst, propertyMap);
        }

        final Assrt com = propertyMap.get(template);

        if (com != null)
            return;

        output.dbgMessage(dbg, "I-DBG commutative assrts: %s: %s", assrt,
            assrt.getFormula());
        propertyMap.put(template, assrt);
    }

    // ------------------------------------------------------------------------
    // ----------------------------Detection-----------------------------------
    // ------------------------------------------------------------------------

    /**
     * @param first The one operand
     * @param second The other operand
     * @return -1(less), 0(equal),1(greater)
     */
    public static int compareNodes(final ParseNode first, final ParseNode second)
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
    // ------------------------------------------------------------------------
    // ----------------------------Transformations-----------------------------
    // ------------------------------------------------------------------------

    // f(a, b) = f(b, a))
    public ProofStepStmt closurePropertyCommutative(final WorksheetInfo info,
        final GeneralizedStmt comProp, final Assrt comAssrt,
        final ParseNode stepNode)
    {
        final ProofStepStmt[] hyps;
        if (!comProp.template.isEmpty()) {

            hyps = new ProofStepStmt[3];
            final int n0 = comProp.varIndexes[0];
            final int n1 = comProp.varIndexes[1];
            final ParseNode side = stepNode.getChild()[0];
            final ParseNode[] in = new ParseNode[2];
            in[0] = side.getChild()[n0];
            in[1] = side.getChild()[n1];

            for (int i = 0; i < 2; i++)
                hyps[i] = clInfo.closureProperty(info, comProp, in[i]);
        }
        else
            hyps = new ProofStepStmt[]{};

        final ProofStepStmt res = info.getOrCreateProofStepStmt(stepNode, hyps,
            comAssrt);

        return res;
    }

    // ------------------------------------------------------------------------
    // ------------------------------Getters-----------------------------------
    // ------------------------------------------------------------------------

    public Assrt getComOp(final GeneralizedStmt genStmt) {
        return getComOp(genStmt.stmt, genStmt.constSubst, genStmt.template);
    }

    public Assrt getComOp(final Stmt stmt, final ConstSubst constSubst,
        final PropertyTemplate template)
    {
        final Map<ConstSubst, Map<PropertyTemplate, Assrt>> constSubstMap = comOp
            .get(stmt);

        if (constSubstMap == null)
            return null;

        final Map<PropertyTemplate, Assrt> propertyMap = constSubstMap
            .get(constSubst);

        if (propertyMap == null)
            return null;

        final Assrt comTr = propertyMap.get(template);
        return comTr;
    }
}
