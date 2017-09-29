package edu.rit.goal.sdg.interpreter;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import edu.rit.goal.sdg.DefUsesUtils;
import edu.rit.goal.sdg.graph.EdgeType;
import edu.rit.goal.sdg.graph.SysDepGraph;
import edu.rit.goal.sdg.graph.Vertex;
import edu.rit.goal.sdg.graph.VertexType;
import edu.rit.goal.sdg.interpreter.params.EmptyParam;
import edu.rit.goal.sdg.interpreter.params.Params;
import edu.rit.goal.sdg.interpreter.stmt.Assign;
import edu.rit.goal.sdg.interpreter.stmt.Break;
import edu.rit.goal.sdg.interpreter.stmt.BreakEdge;
import edu.rit.goal.sdg.interpreter.stmt.Call;
import edu.rit.goal.sdg.interpreter.stmt.CallEdge;
import edu.rit.goal.sdg.interpreter.stmt.ContEdge;
import edu.rit.goal.sdg.interpreter.stmt.Continue;
import edu.rit.goal.sdg.interpreter.stmt.CtrlEdge;
import edu.rit.goal.sdg.interpreter.stmt.CtrlVertex;
import edu.rit.goal.sdg.interpreter.stmt.CtrlVertex.CtrlType;
import edu.rit.goal.sdg.interpreter.stmt.Def;
import edu.rit.goal.sdg.interpreter.stmt.Defer;
import edu.rit.goal.sdg.interpreter.stmt.DoWhile;
import edu.rit.goal.sdg.interpreter.stmt.Expr;
import edu.rit.goal.sdg.interpreter.stmt.For;
import edu.rit.goal.sdg.interpreter.stmt.IfThenElse;
import edu.rit.goal.sdg.interpreter.stmt.Param;
import edu.rit.goal.sdg.interpreter.stmt.ParamIn;
import edu.rit.goal.sdg.interpreter.stmt.ParamOut;
import edu.rit.goal.sdg.interpreter.stmt.PopCtrl;
import edu.rit.goal.sdg.interpreter.stmt.PostOp;
import edu.rit.goal.sdg.interpreter.stmt.PreOp;
import edu.rit.goal.sdg.interpreter.stmt.Return;
import edu.rit.goal.sdg.interpreter.stmt.Seq;
import edu.rit.goal.sdg.interpreter.stmt.Skip;
import edu.rit.goal.sdg.interpreter.stmt.Stmt;
import edu.rit.goal.sdg.interpreter.stmt.Str;
import edu.rit.goal.sdg.interpreter.stmt.Vc;
import edu.rit.goal.sdg.interpreter.stmt.While;
import edu.rit.goal.sdg.interpreter.stmt.sw.DefaultCase;
import edu.rit.goal.sdg.interpreter.stmt.sw.EmptySwitch;
import edu.rit.goal.sdg.interpreter.stmt.sw.ICase;
import edu.rit.goal.sdg.interpreter.stmt.sw.ISwitchBody;
import edu.rit.goal.sdg.interpreter.stmt.sw.MultiCase;
import edu.rit.goal.sdg.interpreter.stmt.sw.MultiSwitch;
import edu.rit.goal.sdg.interpreter.stmt.sw.SingleCase;
import edu.rit.goal.sdg.interpreter.stmt.sw.SingleSwitch;
import edu.rit.goal.sdg.interpreter.stmt.sw.Switch;

public class SimpleInterpreter {

    public static int VTX_ID;

    public static final Program PROGRAM = Programs.simpleDef();
    public static final boolean PRINT = true;
    public static final boolean PRINT_RULES = true;

    private static Set<String> execRules = new HashSet<>();
    public static final String[] RULES = { "defRule", "voidDefRule", "seqSkipRule", "seqDeferRule", "seqSeqDeferRule",
	    "seqRule", "assignCallRule", "assignRule", "ctrlEdgeSeqRule", "ctrlEdgeSkipRule", "ctrlEdgeDeferRule",
	    "ctrlEdgeRule", "ifThenElseRule", "whileRule", "doWhile", "ctrlEdgeDoWhileRule", "forRule",
	    "switchEmptyRule", "switchCaseRule", "switchCasesRule", "breakRule", "breakEdgeLoopRule",
	    "breakEdgeSequentialRule", "continueRule", "continueEdgeLoopRule", "continueEdgeSequentialRule",
	    "popCtrlRule", "popCtrlEmptyRule", "callRule", "paramActualInRule", "paramActualOutRule", "paramFormalRule",
	    "vcRule", "deferRule", "callEdgeRule", "paramInOobRule", "paramInRule", "paramOutRule", "preIncrRule",
	    "postIncrRule", "preDecrRule", "postDecrRule" };

    public static void main(final String[] args) {
	VTX_ID = 0;
	final Program result = interpret(PROGRAM);
	if (PRINT)
	    System.out.println(result.s + "\n");
	final Set<String> allRules = new HashSet<String>(Arrays.asList(RULES));
	allRules.removeAll(execRules);
	final int execSize = RULES.length - allRules.size();
	System.out.println(execSize + "/" + RULES.length + " rules were executed to interpret the program.");
	System.out.println("The following rules were not executed:");
	System.out.println(allRules);
    }

    public static Program interpret(final Program program) {
	execRules.clear();
	Program result = program;
	while (!(result.s instanceof Skip)) {
	    if (PRINT)
		System.out.println(result.s);
	    result = small(result);
	    if (PRINT) {
		System.out.println(result.sdg);
		System.out.println();
	    }
	}
	return result;
    }

    public static Program small(final Program program) {
	Program result = null;
	final Stmt s = program.s;
	if (s instanceof Def) {
	    final boolean hasReturnType = ((Def) s).b;
	    if (hasReturnType) {
		printRule("defRule");
		result = defRule(program);
	    } else {
		printRule("voidDefRule");
		result = voidDefRule(program);
	    }
	} else if (s instanceof Seq) {
	    final Stmt s1 = ((Seq) s).s1;
	    final Stmt s2 = ((Seq) s).s2;
	    if (s1 instanceof Skip) {
		printRule("seqSkipRule");
		result = seqSkipRule(program);
	    } else if (s1 instanceof Defer && !(s2 instanceof Defer)) {
		printRule("seqDeferRule");
		result = seqDeferRule(program);
	    } else if (s1 instanceof Seq && ((Seq) s1).s2 instanceof Defer && !(s2 instanceof Defer)) {
		printRule("seqSeqDeferRule");
		result = seqSeqDeferRule(program);
	    } else {
		printRule("seqRule");
		result = seqRule(program);
	    }
	} else if (s instanceof Assign) {
	    final Expr expr = ((Assign) s).e;
	    if (expr instanceof Call) {
		printRule("assignCallRule");
		result = assignCallRule(program);
	    } else {
		printRule("assignRule");
		result = assignRule(program);
	    }
	} else if (s instanceof CtrlEdge) {
	    final Stmt s2 = ((CtrlEdge) s).s;
	    if (s2 instanceof Seq) {
		printRule("ctrlEdgeSeqRule");
		result = ctrlEdgeSeqRule(program);
	    } else if (s2 instanceof Skip) {
		printRule("ctrlEdgeSkipRule");
		result = ctrlEdgeSkipRule(program);
	    } else if (s2 instanceof Defer) {
		printRule("ctrlEdgeDeferRule");
		result = ctrlEdgeDeferRule(program);
	    } else if (s2 instanceof DoWhile) {
		printRule("ctrlEdgeDoWhileRule");
		result = ctrlEdgeDoWhileRule(program);
	    } else {
		printRule("ctrlEdgeRule");
		result = ctrlEdgeRule(program);
	    }
	} else if (s instanceof IfThenElse) {
	    printRule("ifThenElseRule");
	    result = ifThenElseRule(program);
	} else if (s instanceof While) {
	    printRule("whileRule");
	    result = whileRule(program);
	} else if (s instanceof DoWhile) {
	    printRule("doWhileRule");
	    result = doWhileRule(program);
	} else if (s instanceof For) {
	    printRule("forRule");
	    result = forRule(program);
	} else if (s instanceof Switch) {
	    final ISwitchBody sb = ((Switch) s).sb;
	    if (sb instanceof EmptySwitch) {
		printRule("switchEmptyRule");
		result = switchEmptyRule(program);
	    } else if (sb instanceof SingleSwitch) {
		printRule("switchCaseRule");
		result = switchCaseRule(program);
	    } else if (sb instanceof MultiSwitch) {
		printRule("switchCasesRule");
		result = switchCasesRule(program);
	    }
	} else if (s instanceof Break) {
	    printRule("breakRule");
	    result = breakRule(program);
	} else if (s instanceof BreakEdge) {
	    final BreakEdge s2 = (BreakEdge) s;
	    final CtrlType ct = s2.ct;
	    if (CtrlType.LOOP.equals(ct)) {
		printRule("breakEdgeLoopRule");
		result = breakEdgeLoopRule(program);
	    } else if (CtrlType.SEQ.equals(ct) && !program.C.isEmpty()) {
		printRule("breakEdgeSequentialRule");
		result = breakEdgeSequentialRule(program);
	    }
	} else if (s instanceof Continue) {
	    printRule("continueRule");
	    result = continueRule(program);
	} else if (s instanceof ContEdge) {
	    final ContEdge s2 = (ContEdge) s;
	    final CtrlType ct = s2.cv.ct;
	    if (CtrlType.LOOP.equals(ct)) {
		printRule("continueEdgeLoopRule");
		result = continueEdgeLoopRule(program);
	    } else if (CtrlType.SEQ.equals(ct)) {
		printRule("continueEdgeSequentialRule");
		result = continueEdgeSequentialRule(program);
	    }
	} else if (s instanceof PopCtrl) {
	    if (program.C.isEmpty()) {
		printRule("popCtrlEmptyRule");
		result = popCtrlEmptyRule(program);
	    } else {
		printRule("popCtrlRule");
		result = popCtrlRule(program);
	    }
	} else if (s instanceof Call) {
	    printRule("callRule");
	    result = callRule(program);
	} else if (s instanceof Param) {
	    final Param param = (Param) s;
	    final VertexType vtxType = param.t;
	    if (VertexType.ACTUAL_IN.equals(vtxType)) {
		printRule("paramActualInRule");
		result = paramActualInRule(program);
	    } else if (VertexType.ACTUAL_OUT.equals(vtxType)) {
		printRule("paramActualOutRule");
		result = paramActualOutRule(program);
	    } else if (VertexType.FORMAL_IN.equals(vtxType) || VertexType.FORMAL_OUT.equals(vtxType)) {
		printRule("paramFormalRule");
		result = paramFormalRule(program);
	    }
	} else if (s instanceof Vc) {
	    printRule("vcRule");
	    result = vcRule(program);
	} else if (s instanceof Defer) {
	    printRule("deferRule");
	    result = deferRule(program);
	} else if (s instanceof CallEdge) {
	    printRule("callEdgeRule");
	    result = callEdgeRule(program);
	} else if (s instanceof ParamIn) {
	    final ParamIn paramIn = (ParamIn) s;
	    final LinkedHashSet<Vertex> Px = program.P.get(paramIn.x);
	    if (Px == null || paramIn.i >= paramIn.V.size() || paramIn.V.size() > Px.size()) {
		printRule("paramInOobRule");
		result = paramInOobRule(program);
	    } else if (paramIn.i >= 1 && Px.size() > paramIn.i) {
		printRule("paramInRule");
		result = paramInRule(program);
	    }
	} else if (s instanceof ParamOut) {
	    printRule("paramOutRule");
	    result = paramOutRule(program);
	} else if (s instanceof Return) {
	    printRule("returnRule");
	    result = returnRule(program);
	} else if (s instanceof PreOp) {
	    printRule("preOpRule");
	    result = preOpRule(program);
	} else if (s instanceof PostOp) {
	    printRule("postOpRule");
	    result = postOpRule(program);
	}
	return result;
    }

    private static Program defRule(final Program program) {
	final Def s = (Def) program.s;
	final Vertex v = new Vertex(VTX_ID++, VertexType.ENTRY, s.x);
	program.sdg.addVertex(v);
	final Param param1 = new Param(s.x, VertexType.FORMAL_OUT, new Str(s.x + "ResultOut"));
	final Param param2 = new Param(s.x, VertexType.FORMAL_IN, new Params(s.x + "ResultIn", s.p));
	final Seq seq1 = new Seq(param1, new Seq(param2, s.s));
	final CtrlEdge ctrlEdge = new CtrlEdge(true, v, seq1);
	final Seq seq2 = new Seq(ctrlEdge, new PopCtrl());
	final CtrlVertex cv = new CtrlVertex(v, CtrlType.SEQ);
	program.C.push(cv);
	return new Program(program.sdg, program.Vc, program.P, program.C, new CtrlEdge(true, v, seq2));
    }

    private static Program voidDefRule(final Program program) {
	final Def s = (Def) program.s;
	final Vertex v = new Vertex(VTX_ID++, VertexType.ENTRY, s.x);
	program.sdg.addVertex(v);
	final Param param = new Param(s.x, VertexType.FORMAL_IN, s.p);
	final Seq seq1 = new Seq(param, s.s);
	final CtrlEdge ctrlEdge = new CtrlEdge(true, v, seq1);
	final Seq seq2 = new Seq(ctrlEdge, new PopCtrl());
	final CtrlVertex cv = new CtrlVertex(v, CtrlType.SEQ);
	program.C.push(cv);
	return new Program(program.sdg, program.Vc, program.P, program.C, new CtrlEdge(true, v, seq2));
    }

    protected static Program seqRule(final Program program) {
	final Seq seq = (Seq) program.s;
	final Program s1Program = new Program(program.sdg, program.Vc, program.P, program.C, seq.s1);
	final Program p = small(s1Program);
	return new Program(p.sdg, p.Vc, p.P, program.C, new Seq(p.s, seq.s2));
    }

    protected static Program seqSkipRule(final Program program) {
	return new Program(program.sdg, program.Vc, program.P, program.C, ((Seq) program.s).s2);
    }

    private static Program assignRule(final Program program) {
	final Assign assign = (Assign) program.s;
	final Vertex va = new Vertex(VTX_ID++, VertexType.ASSIGN, assign.x + assign.op + assign.e);
	va.setAssignedVariable(assign.getDef());
	va.setReadingVariables(assign.getUses());
	program.sdg.addVertex(va);
	program.Vc.add(va);
	return new Program(program.sdg, program.Vc, program.P, program.C, new Skip());
    }

    private static Program preOpRule(final Program program) {
	final PreOp preOp = (PreOp) program.s;
	final Vertex v = new Vertex(VTX_ID++, VertexType.ASSIGN, preOp.op + preOp.x);
	v.setAssignedVariable(preOp.getDef());
	v.setReadingVariables(preOp.getUses());
	program.sdg.addVertex(v);
	program.Vc.add(v);
	return new Program(program.sdg, program.Vc, program.P, program.C, new Skip());
    }

    private static Program postOpRule(final Program program) {
	final PostOp postOp = (PostOp) program.s;
	final Vertex v = new Vertex(VTX_ID++, VertexType.ASSIGN, postOp.x + postOp.op);
	v.setAssignedVariable(postOp.getDef());
	v.setReadingVariables(postOp.getUses());
	program.sdg.addVertex(v);
	program.Vc.add(v);
	return new Program(program.sdg, program.Vc, program.P, program.C, new Skip());
    }

    private static Program returnRule(final Program program) {
	final Return ret = (Return) program.s;
	final Vertex v = new Vertex(VTX_ID++, VertexType.RETURN, ret.e);
	v.setReadingVariables(ret.getUses());
	program.sdg.addVertex(v);
	program.Vc.add(v);
	return new Program(program.sdg, program.Vc, program.P, program.C, new Skip());
    }

    private static Program vcRule(final Program program) {
	final Vc vc = (Vc) program.s;
	program.Vc.add(vc.v);
	return new Program(program.sdg, program.Vc, program.P, program.C, new Skip());
    }

    private static Program ctrlEdgeRule(final Program program) {
	final CtrlEdge ctrledge = (CtrlEdge) program.s;
	final Stmt s = ctrledge.s;
	final Program p = small(new Program(program.sdg, program.Vc, program.P, program.C, s));
	if (p == null)
	    System.out.println(p);
	final CtrlEdge ctrlEdge = new CtrlEdge(ctrledge.B, ctrledge.N, p.s);
	return new Program(p.sdg, p.Vc, program.P, program.C, ctrlEdge);
    }

    private static Program ctrlEdgeSkipRule(final Program program) {
	final CtrlEdge s = (CtrlEdge) program.s;
	createEdges(program.sdg, s.B, s.N, program.Vc);
	return new Program(program.sdg, new HashSet<>(), program.P, program.C, new Skip());
    }

    private static Program ctrlEdgeSeqRule(final Program program) {
	final CtrlEdge s = (CtrlEdge) program.s;
	final Seq seq = (Seq) s.s;
	return new Program(program.sdg, program.Vc, program.P, program.C,
		new Seq(new CtrlEdge(s.B, s.N, seq.s1), new CtrlEdge(s.B, s.N, seq.s2)));
    }

    private static Program ifThenElseRule(final Program program) {
	final IfThenElse s = (IfThenElse) program.s;
	final Vertex v = new Vertex(VTX_ID++, VertexType.CTRL_IF, s.e.toString());
	v.setAssignedVariable(s.getDef());
	v.setReadingVariables(s.getUses());
	program.sdg.addVertex(v);
	program.Vc.clear();
	final CtrlVertex cv = new CtrlVertex(v, CtrlType.SEQ);
	program.C.push(cv);
	final CtrlEdge ctrl1 = new CtrlEdge(true, v, s.s1);
	final CtrlEdge ctrl2 = new CtrlEdge(false, v, s.s2);
	final Seq seq = new Seq(ctrl1, new Seq(ctrl2, new Seq(new Vc(v), new PopCtrl())));
	return new Program(program.sdg, program.Vc, program.P, program.C, seq);
    }

    private static Program whileRule(final Program program) {
	final While s = (While) program.s;
	final Vertex v = new Vertex(VTX_ID++, VertexType.CTRL_WHILE, s.e.toString());
	program.sdg.addVertex(v);
	program.sdg.addEdge(v, v, EdgeType.CTRL_TRUE);
	program.Vc.clear();
	final CtrlVertex cv = new CtrlVertex(v, CtrlType.LOOP);
	program.C.push(cv);
	final CtrlEdge ctrl = new CtrlEdge(true, v, s.s);
	final Seq seq = new Seq(ctrl, new Seq(new Vc(v), new PopCtrl()));
	return new Program(program.sdg, program.Vc, program.P, program.C, seq);
    }

    private static Program doWhileRule(final Program program) {
	final DoWhile s = (DoWhile) program.s;
	final Vertex v = new Vertex(VTX_ID++, VertexType.CTRL_DO, s.e.toString());
	program.sdg.addVertex(v);
	program.sdg.addEdge(v, v, EdgeType.CTRL_TRUE);
	program.Vc.clear();
	final CtrlVertex cv = new CtrlVertex(v, CtrlType.LOOP);
	program.C.push(cv);
	final CtrlEdge ctrl = new CtrlEdge(true, v, s.s);
	final Seq seq = new Seq(ctrl, new Seq(new Vc(v), new PopCtrl()));
	return new Program(program.sdg, program.Vc, program.P, program.C, seq);
    }

    private static Program ctrlEdgeDoWhileRule(final Program program) {
	final CtrlEdge s = (CtrlEdge) program.s;
	final DoWhile doWhile = (DoWhile) s.s;
	final Vertex v = new Vertex(VTX_ID++, VertexType.CTRL_DO, doWhile.e.toString());
	program.sdg.addVertex(v);
	program.sdg.addEdge(v, v, EdgeType.CTRL_TRUE);
	program.Vc.clear();
	final CtrlVertex cv = new CtrlVertex(v, CtrlType.LOOP);
	program.C.push(cv);
	final List<Boolean> B = new LinkedList<>(s.B);
	B.add(true);
	final List<Vertex> N = new LinkedList<>(s.N);
	N.add(v);
	final Stmt doWhileStmt = doWhile.s;
	final CtrlEdge ctrl = new CtrlEdge(B, N, doWhileStmt);
	final Seq seq = new Seq(ctrl, new Seq(new Vc(v), new PopCtrl()));
	return new Program(program.sdg, program.Vc, program.P, program.C, seq);
    }

    private static Program forRule(final Program program) {
	final For s = (For) program.s;
	final Vertex v = new Vertex(VTX_ID++, VertexType.CTRL_FOR, s.sc.toString());
	v.setAssignedVariable(s.sc.getDef());
	v.setReadingVariables(s.sc.getUses());
	program.sdg.addVertex(v);
	program.sdg.addEdge(v, v, EdgeType.CTRL_TRUE);
	program.Vc.clear();
	final CtrlVertex cv = new CtrlVertex(v, CtrlType.LOOP);
	program.C.push(cv);
	final Seq seq3 = new Seq(new Vc(v), new PopCtrl());
	final Seq seq2 = new Seq(s.si, seq3);
	final CtrlEdge ctrlEdge = new CtrlEdge(true, v, new Seq(s.su, s.s));
	final Seq seq = new Seq(ctrlEdge, seq2);
	return new Program(program.sdg, program.Vc, program.P, program.C, seq);
    }

    private static Program switchEmptyRule(final Program program) {
	final Switch s = (Switch) program.s;
	final Vertex v = new Vertex(VTX_ID++, VertexType.CTRL_IF, s.e.toString());
	program.sdg.addVertex(v);
	program.Vc.add(v);
	return new Program(program.sdg, program.Vc, program.P, program.C, new Skip());
    }

    private static Program switchCaseRule(final Program program) {
	final Switch s = (Switch) program.s;
	final SingleSwitch ss = (SingleSwitch) s.sb;
	final String x = largeCsCond(s.e, ss.cs);
	final IfThenElse ifThenElse = new IfThenElse(new Str(x), ss.s, new Skip());
	ifThenElse.setUses(s.getUses());
	return new Program(program.sdg, program.Vc, program.P, program.C, ifThenElse);
    }

    private static Program switchCasesRule(final Program program) {
	final Switch s = (Switch) program.s;
	final MultiSwitch sb = (MultiSwitch) s.sb;
	final Switch sw = new Switch(s.e, sb.ss);
	final Program antProgram = new Program(program.sdg, program.Vc, program.P, program.C, sw);
	final Program p = small(antProgram);
	return new Program(p.sdg, p.Vc, p.P, p.C, new Switch(s.e, sb.sb));
    }

    private static Program breakRule(final Program program) {
	final Vertex v = new Vertex(VTX_ID++, VertexType.BREAK, "break");
	program.sdg.addVertex(v);
	program.Vc.add(v);
	final CtrlVertex cv = program.C.pop();
	final Deque<CtrlVertex> S = new ArrayDeque<CtrlVertex>(program.C);
	S.push(cv);
	final BreakEdge breakEdge = new BreakEdge(cv.ct, v, S);
	return new Program(program.sdg, program.Vc, program.P, program.C, breakEdge);
    }

    private static Program breakEdgeLoopRule(final Program program) {
	final BreakEdge s = (BreakEdge) program.s;
	final CtrlVertex cv = program.C.pop();
	final Vertex v1 = cv.v;
	program.sdg.addEdge(s.v, v1, EdgeType.CTRL_TRUE);
	return new Program(program.sdg, program.Vc, program.P, s.S, new Skip());
    }

    private static Program breakEdgeSequentialRule(final Program program) {
	final BreakEdge s = (BreakEdge) program.s;
	final CtrlVertex cv = program.C.pop();
	final BreakEdge breakEdge = new BreakEdge(cv.ct, s.v, s.S);
	return new Program(program.sdg, program.Vc, program.P, program.C, breakEdge);
    }

    private static Program continueRule(final Program program) {
	final Vertex v = new Vertex(VTX_ID++, VertexType.CONTINUE, "continue");
	program.sdg.addVertex(v);
	program.Vc.add(v);
	final CtrlVertex cv = program.C.pop();
	final Deque<CtrlVertex> S = new ArrayDeque<CtrlVertex>(program.C);
	S.add(cv);
	final ContEdge contEdge = new ContEdge(cv, v, S);
	return new Program(program.sdg, program.Vc, program.P, program.C, contEdge);
    }

    private static Program continueEdgeLoopRule(final Program program) {
	final ContEdge s = (ContEdge) program.s;
	program.sdg.addEdge(s.v, s.cv.v, EdgeType.CTRL_TRUE);
	return new Program(program.sdg, program.Vc, program.P, s.S, new Skip());
    }

    private static Program continueEdgeSequentialRule(final Program program) {
	final ContEdge s = (ContEdge) program.s;
	final CtrlVertex cv = program.C.pop();
	final ContEdge contEdge = new ContEdge(cv, s.v, s.S);
	return new Program(program.sdg, program.Vc, program.P, program.C, contEdge);
    }

    private static Program popCtrlRule(final Program program) {
	program.C.pop();
	return new Program(program.sdg, program.Vc, program.P, program.C, new Skip());
    }

    private static Program popCtrlEmptyRule(final Program program) {
	return new Program(program.sdg, program.Vc, program.P, program.C, new Skip());
    }

    private static Program paramActualInRule(final Program program) {
	final Param param = (Param) program.s;
	final LinkedHashSet<Vertex> Vp = largeParam(param);
	for (final Vertex v : Vp) {
	    program.sdg.addVertex(v);
	    program.Vc.add(v);
	}
	return new Program(program.sdg, program.Vc, program.P, program.C, new Defer(new ParamIn(param.x, 1, Vp)));
    }

    private static Program paramActualOutRule(final Program program) {
	final Param param = (Param) program.s;
	final Set<Vertex> Vp = largeParam(param);
	for (final Vertex v : Vp) {
	    program.sdg.addVertex(v);
	    program.Vc.add(v);
	}
	final Vertex Vp0 = Vp.iterator().next();
	return new Program(program.sdg, program.Vc, program.P, program.C, new Defer(new ParamOut(Vp0, param.x)));
    }

    private static Program paramFormalRule(final Program program) {
	final Param param = (Param) program.s;
	final Set<Vertex> Vp = largeParam(param);
	LinkedHashSet<Vertex> paramVtcs = program.P.get(param.x);
	if (paramVtcs == null) {
	    paramVtcs = new LinkedHashSet<>();
	}
	for (final Vertex v : Vp) {
	    program.sdg.addVertex(v);
	    program.Vc.add(v);
	    paramVtcs.add(v);
	}
	program.P.put(param.x, paramVtcs);
	return new Program(program.sdg, program.Vc, program.P, program.C, new Skip());
    }

    private static Program paramInRule(final Program program) {
	final ParamIn paramIn = (ParamIn) program.s;
	final Vertex v = vtxAtIdx(program.P.get(paramIn.x), paramIn.i);
	final Vertex Vi = vtxAtIdx(paramIn.V, paramIn.i - 1);
	program.sdg.addEdge(Vi, v, EdgeType.PARAM_IN);
	return new Program(program.sdg, program.Vc, program.P, program.C,
		new ParamIn(paramIn.x, paramIn.i + 1, paramIn.V));
    }

    private static Program paramInOobRule(final Program program) {
	return new Program(program.sdg, program.Vc, program.P, program.C, new Skip());
    }

    private static Program paramOutRule(final Program program) {
	final ParamOut paramOut = (ParamOut) program.s;
	final LinkedHashSet<Vertex> Px = program.P.get(paramOut.x);
	if (Px != null && !Px.isEmpty()) {
	    final Vertex Px0 = vtxAtIdx(program.P.get(paramOut.x), 0);
	    if (VertexType.FORMAL_OUT.equals(Px0.getType())) {
		program.sdg.addEdge(Px0, paramOut.v, EdgeType.PARAM_OUT);
	    } else {
		System.out.println("[WARN] " + Px0 + " type is '" + Px0.getType() + "'. Expected was 'FORMAL_OUT'.");
	    }
	} else {
	    System.out.println("[WARN] Cannot find parameter vertices for method '" + paramOut.x + "'.");
	}
	return new Program(program.sdg, program.Vc, program.P, program.C, new Skip());
    }

    private static Program callRule(final Program program) {
	final Call s = (Call) program.s;
	final Vertex vc = new Vertex(VTX_ID++, VertexType.CALL, s.toString());
	vc.setAssignedVariable(s.getDef());
	vc.setReadingVariables(s.getUses());
	program.sdg.addVertex(vc);
	final Seq seq2 = new Seq(new Vc(vc), new Defer(new CallEdge(vc, s.x)));
	final Param param = new Param(s.x, VertexType.ACTUAL_IN, s.p);
	final Seq seq = new Seq(new CtrlEdge(true, vc, param), seq2);
	return new Program(program.sdg, program.Vc, program.P, program.C, seq);
    }

    private static Program assignCallRule(final Program program) {
	final Assign assign = (Assign) program.s;
	final Call call = (Call) assign.e;
	final Vertex va = new Vertex(VTX_ID++, VertexType.CALL, assign.x + assign.op + call.x + "(" + call.p + ")");
	va.setAssignedVariable(assign.getDef());
	va.setReadingVariables(assign.getUses());
	program.sdg.addVertex(va);
	final Param param1 = new Param(call.x, VertexType.ACTUAL_OUT, new Str(assign.x));
	final Param param2 = new Param(call.x, VertexType.ACTUAL_IN, new Params(assign.x, call.p));
	final Seq seq2 = new Seq(param1, param2);
	final Seq seq3 = new Seq(new Vc(va), new Defer(new CallEdge(va, call.x)));
	final Seq seq = new Seq(new CtrlEdge(true, va, seq2), seq3);
	return new Program(program.sdg, program.Vc, program.P, program.C, seq);
    }

    private static Program callEdgeRule(final Program program) {
	final CallEdge callEdge = (CallEdge) program.s;
	final Optional<Vertex> ve = program.sdg.vertexSet().stream()
		.filter(v -> v.getType().equals(VertexType.ENTRY) && v.getLabel().equals(callEdge.x)).findFirst();
	if (!ve.isPresent())
	    System.out.println("[WARN] Cannot find ENTER vertex for method '" + callEdge.x + "'");
	else
	    program.sdg.addEdge(callEdge.v, ve.get(), EdgeType.CALL);
	return new Program(program.sdg, program.Vc, program.P, program.C, new Skip());
    }

    private static Program deferRule(final Program program) {
	final Defer defer = (Defer) program.s;
	return new Program(program.sdg, program.Vc, program.P, program.C, defer.s);
    }

    private static Program seqDeferRule(final Program program) {
	final Seq seq = (Seq) program.s;
	return new Program(program.sdg, program.Vc, program.P, program.C, new Seq(seq.s2, seq.s1));
    }

    private static Program seqSeqDeferRule(final Program program) {
	final Seq seq = (Seq) program.s;
	final Stmt s1 = ((Seq) seq.s1).s1;
	final Defer s2 = (Defer) ((Seq) seq.s1).s2;
	final Stmt s3 = seq.s2;
	return new Program(program.sdg, program.Vc, program.P, program.C, new Seq(s1, new Seq(s3, s2)));
    }

    private static Program ctrlEdgeDeferRule(final Program program) {
	final CtrlEdge s = (CtrlEdge) program.s;
	final Defer d = (Defer) s.s;
	final Defer defer = new Defer(new CtrlEdge(s.B, s.N, d.s));
	final CtrlEdge ctrlEdge = new CtrlEdge(s.B, s.N, new Skip());
	final Seq seq = new Seq(ctrlEdge, defer);
	return new Program(program.sdg, program.Vc, program.P, program.C, seq);
    }

    // Large-step

    private static LinkedHashSet<Vertex> largeNoParamRule(final Param param) {
	return new LinkedHashSet<>();
    }

    private static LinkedHashSet<Vertex> largeParamRule(final Param param) {
	final Str str = (Str) param.p;
	final LinkedHashSet<Vertex> V = new LinkedHashSet<>();
	final Vertex v = new Vertex(VTX_ID++, param.t, str.value);
	// Def and uses for data dependencies
	v.setAssignedVariable(str.getDef());
	v.setReadingVariables(str.getUses());
	DefUsesUtils.paramsDefUses(v, str.value);
	V.add(v);
	return V;
    }

    private static LinkedHashSet<Vertex> largeParamsRule(final Param param) {
	final Params params = (Params) param.p;
	final LinkedHashSet<Vertex> V = largeParam(new Param(param.t, params.p));
	final Vertex v = new Vertex(VTX_ID++, param.t, params.x);
	// Def and uses for data dependencies
	v.setAssignedVariable(params.getDef());
	v.setReadingVariables(params.getUses());
	DefUsesUtils.paramsDefUses(v, params.x);
	V.add(v);
	return V;
    }

    private static LinkedHashSet<Vertex> largeParam(final Param param) {
	LinkedHashSet<Vertex> result = null;
	final edu.rit.goal.sdg.interpreter.params.Param p = (edu.rit.goal.sdg.interpreter.params.Param) param.p;
	if (p instanceof EmptyParam) {
	    result = largeNoParamRule(param);
	} else if (p instanceof Str) {
	    result = largeParamRule(param);
	} else if (p instanceof Params) {
	    result = largeParamsRule(param);
	}
	return result;
    }

    private static String largeCsCond(final Expr e, final ICase cs) {
	String result = null;
	if (cs instanceof DefaultCase) {
	    result = "default";
	} else if (cs instanceof SingleCase) {
	    result = csCondCaseRule(e, cs);
	} else if (cs instanceof MultiCase) {
	    result = csCondCasesRule(e, cs);
	}
	return result;
    }

    private static String csCondCaseRule(final Expr e, final ICase cs) {
	final SingleCase s = (SingleCase) cs;
	return e + "==" + s.x;
    }

    private static String csCondCasesRule(final Expr e, final ICase cs) {
	final MultiCase s = (MultiCase) cs;
	final String x1 = largeCsCond(e, s.x);
	final String x2 = largeCsCond(e, s.cs);
	return x1 + "||" + x2;
    }

    // Helper methods

    private static void createEdges(final SysDepGraph sdg, final List<Boolean> B, final List<Vertex> sources,
	    final Set<Vertex> targets) {
	for (final Boolean b : B) {
	    for (final Vertex s : sources) {
		for (final Vertex t : targets) {
		    final EdgeType type = b ? EdgeType.CTRL_TRUE : EdgeType.CTRL_FALSE;
		    sdg.addEdge(s, t, type);
		}
	    }
	}
    }

    private static Vertex vtxAtIdx(final LinkedHashSet<Vertex> V, int idx) {
	final Iterator<Vertex> it = V.iterator();
	while (idx > 0) {
	    it.next();
	    idx--;
	}
	return it.next();
    }

    private static void printRule(final String rule) {
	execRules.add(rule);
	if (PRINT && PRINT_RULES)
	    System.out.println(rule);
    }

}