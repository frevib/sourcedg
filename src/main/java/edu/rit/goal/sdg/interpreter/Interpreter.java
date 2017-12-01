package edu.rit.goal.sdg.interpreter;

import static edu.rit.goal.sdg.graph.VertexType.ACTUAL_IN;
import static edu.rit.goal.sdg.graph.VertexType.ACTUAL_OUT;
import static edu.rit.goal.sdg.graph.VertexType.FORMAL_IN;
import static edu.rit.goal.sdg.graph.VertexType.FORMAL_OUT;
import static edu.rit.goal.sdg.interpreter.pattern.BooleanPattern.caseof;
import static edu.rit.goal.sdg.interpreter.pattern.ClassPattern.caseof;
import static edu.rit.goal.sdg.interpreter.pattern.OtherwisePattern.otherwise;
import static edu.rit.goal.sdg.interpreter.pattern.PatternMatching.$;
import static edu.rit.goal.sdg.interpreter.pattern.VertexTypePattern.caseof;
import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import org.jgrapht.graph.DefaultDirectedGraph;
import edu.rit.goal.sdg.DefUsesUtils;
import edu.rit.goal.sdg.graph.DefaultVertexCreator;
import edu.rit.goal.sdg.graph.Edge;
import edu.rit.goal.sdg.graph.EdgeType;
import edu.rit.goal.sdg.graph.SysDepGraph;
import edu.rit.goal.sdg.graph.Vertex;
import edu.rit.goal.sdg.graph.VertexCreator;
import edu.rit.goal.sdg.graph.VertexType;
import edu.rit.goal.sdg.interpreter.params.EmptyParam;
import edu.rit.goal.sdg.interpreter.params.Params;
import edu.rit.goal.sdg.interpreter.pattern.PatternMatching;
import edu.rit.goal.sdg.interpreter.stmt.Assign;
import edu.rit.goal.sdg.interpreter.stmt.Break;
import edu.rit.goal.sdg.interpreter.stmt.CUnit;
import edu.rit.goal.sdg.interpreter.stmt.Call;
import edu.rit.goal.sdg.interpreter.stmt.CallEdge;
import edu.rit.goal.sdg.interpreter.stmt.CfgEdge;
import edu.rit.goal.sdg.interpreter.stmt.Cls;
import edu.rit.goal.sdg.interpreter.stmt.Continue;
import edu.rit.goal.sdg.interpreter.stmt.CtrlEdge;
import edu.rit.goal.sdg.interpreter.stmt.Decl;
import edu.rit.goal.sdg.interpreter.stmt.Def;
import edu.rit.goal.sdg.interpreter.stmt.DoWhile;
import edu.rit.goal.sdg.interpreter.stmt.EdgeStmt;
import edu.rit.goal.sdg.interpreter.stmt.Expr;
import edu.rit.goal.sdg.interpreter.stmt.Fed;
import edu.rit.goal.sdg.interpreter.stmt.For;
import edu.rit.goal.sdg.interpreter.stmt.IfThenElse;
import edu.rit.goal.sdg.interpreter.stmt.Import;
import edu.rit.goal.sdg.interpreter.stmt.Io;
import edu.rit.goal.sdg.interpreter.stmt.IoUnion;
import edu.rit.goal.sdg.interpreter.stmt.Param;
import edu.rit.goal.sdg.interpreter.stmt.ParamIn;
import edu.rit.goal.sdg.interpreter.stmt.ParamOut;
import edu.rit.goal.sdg.interpreter.stmt.Pkg;
import edu.rit.goal.sdg.interpreter.stmt.PostOp;
import edu.rit.goal.sdg.interpreter.stmt.PreOp;
import edu.rit.goal.sdg.interpreter.stmt.Return;
import edu.rit.goal.sdg.interpreter.stmt.Seq;
import edu.rit.goal.sdg.interpreter.stmt.Skip;
import edu.rit.goal.sdg.interpreter.stmt.Stmt;
import edu.rit.goal.sdg.interpreter.stmt.Str;
import edu.rit.goal.sdg.interpreter.stmt.Vc;
import edu.rit.goal.sdg.interpreter.stmt.Ve;
import edu.rit.goal.sdg.interpreter.stmt.While;
import edu.rit.goal.sdg.interpreter.stmt.sw.DefaultCase;
import edu.rit.goal.sdg.interpreter.stmt.sw.EmptySwitch;
import edu.rit.goal.sdg.interpreter.stmt.sw.ICase;
import edu.rit.goal.sdg.interpreter.stmt.sw.MultiCase;
import edu.rit.goal.sdg.interpreter.stmt.sw.MultiSwitch;
import edu.rit.goal.sdg.interpreter.stmt.sw.SingleCase;
import edu.rit.goal.sdg.interpreter.stmt.sw.SingleSwitch;
import edu.rit.goal.sdg.interpreter.stmt.sw.Switch;;

public class Interpreter {

  public final boolean PRINT = false;
  public final boolean PRINT_RULES = true;
  private final Stmt originalStmt;
  private final VertexCreator vertexCreator;
  private final HashMap<String, Vertex> mEntryVertices;

  public Interpreter(final File file) {
    this(new Translator(file).parse());
  }

  public Interpreter(final Stmt stmt) {
    this(stmt, new DefaultVertexCreator());
  }

  public Interpreter(final Stmt stmt, final VertexCreator vertexCreator) {
    originalStmt = stmt;
    this.vertexCreator = vertexCreator;
    mEntryVertices = new HashMap<>();
  }

  public Program interpret() {
    vertexCreator.resetId();
    final Program program = new Program(originalStmt);
    final Program result = _interpret(program);
    result.sdg.computeDataDeps(vertexCreator.getId());
    return result;
  }

  private Program _interpret(final Program program) {
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
    if (!result.sd.isEmpty()) {
      final Stmt deferSeq = Translator.seq(result.sd);
      result.s = deferSeq;
      result.sd.clear();
      result = _interpret(result);
    }
    result.sdg.setCfgs(result.F);
    return result;
  }

  //@formatter:off
  private final PatternMatching edge = $(
      caseof(Seq.class,
          __ -> this::edgeSeqRule),
      caseof(Skip.class,
          __ -> this::edgeSkipRule),
      otherwise(
          __ -> this::edgeRule)
    );
  
  private final PatternMatching def = $(
      caseof(true, 
          __ -> this::defRule), 
      caseof(false, 
          __ -> this::voidDefRule)
    );
  
  private final PatternMatching seq = $(
      caseof(Io.class, 
          __ -> this::seqIoRule),
      caseof(Skip.class,
          __ -> this::seqSkipRule),
      otherwise(
          __ -> this::seqRule)
    );
  
  private final PatternMatching assign = $(
      caseof(Call.class, 
          __ -> this::assignCallRule),
      otherwise(
          __ -> this::assignRule)
    );
  
  private final PatternMatching ctrlEdge = $(
      caseof(Seq.class, 
          __ -> this::ctrlEdgeSeqRule),
      caseof(Skip.class, 
          __ -> this::ctrlEdgeSkipRule),
      caseof(Io.class, 
          __ -> this::ctrlEdgeIoRule),
      caseof(DoWhile.class, 
          __ -> this::ctrlEdgeDoWhileRule),
      otherwise(
          __ -> this::ctrlEdgeRule)
    );
  
  private final PatternMatching $switch = $(
      caseof(EmptySwitch.class, 
          __ -> this::switchEmptyRule),
      caseof(SingleSwitch.class, 
          __ -> this::switchCaseRule),
      caseof(MultiSwitch.class, 
          __ -> this::switchCasesRule)
    );
  
  private final PatternMatching param = $(
      caseof(ACTUAL_IN, 
          __ -> this::paramActualInRule),
      caseof(ACTUAL_OUT, 
          __ -> this::paramActualOutRule),
      caseof(FORMAL_IN, 
          __ -> this::paramFormalInRule),
      caseof(FORMAL_OUT, 
          __ -> this::paramFormalOutRule)
    );
  
  private final PatternMatching cfgEdge = $(
      caseof(Io.class, 
          s2 -> {
                  final Set<Vertex> ioS1 = ((Io) s2).I;
                  final Set<Vertex> ioS2 = ((Io) s2).O;
                  if (ioS1 == null)
                    return this::cfgEdgeIoCopyInRule;
                  else if (ioS2 == null)
                    return this::cfgEdgeIoCopyOutRule;
                  else
                    return this::cfgEdgeIoIoRule;
                }),
      caseof(Fed.class, 
          ____ -> this::cfgEdgeIoEndDefRule),
      otherwise(
          ____ -> this::cfgEdgeIoRule)
    );
  
  private final PatternMatching ioUnion = $(
      caseof(Io.class, 
          ___ -> this::ioUnionIoIoRule),
      otherwise(
          ___ -> this::ioUnionIoRule)
    );
  
  private final PatternMatching pm = $(
          caseof(EdgeStmt.class, 
              s -> edge.matchFor(s.s)),
          caseof(CUnit.class,
              __ -> this::compilationUnitRule),
          caseof(Pkg.class,
              __ -> this::packageRule),
          caseof(Import.class,
              __ -> this::importRule),
          caseof(Cls.class,
              __ -> this::classRule),
          caseof(Def.class, 
              s -> def.matchFor(s.b)),
          caseof(Seq.class,
              s -> seq.matchFor(s.s1)),
          caseof(Decl.class,
              __ -> this::declRule),
          caseof(Assign.class,
              s -> assign.matchFor(s.e)),
          caseof(CtrlEdge.class,
              s -> ctrlEdge.matchFor(s.s)),
          caseof(IfThenElse.class,
              __ -> this::ifThenElseRule),
          caseof(While.class,
              __ -> this::whileRule),
          caseof(DoWhile.class,
              __ -> this::doWhileRule),
          caseof(For.class,
              __ -> this::forRule),
          caseof(Switch.class,
              s -> $switch.matchFor(s.sb)),
          caseof(Break.class,
              __ -> this::breakRule),
          caseof(Continue.class,
              __ -> this::continueRule),
          caseof(Call.class,
              __ -> this::callRule),
          caseof(Param.class,
              s -> param.matchFor(s.t)),
          caseof(Vc.class,
              __ -> this::vcRule),
          caseof(Ve.class,
              __ -> this::veRule),
          caseof(CallEdge.class,
              __ -> this::callEdgeRule),
          caseof(ParamIn.class,
              __ -> this::paramInRule),
          caseof(ParamOut.class,
              __ -> this::paramOutRule),
          caseof(Return.class,
              __ -> this::returnRule),
          caseof(PreOp.class,
              __ -> this::preOpRule),
          caseof(PostOp.class, 
              __ -> this::postOpRule),
          caseof(Io.class,
              __ -> this::ioRule),
          caseof(CfgEdge.class,
              s -> $(
                     caseof(Skip.class, 
                         __ -> this::cfgEdgeSkipSecondRule),
                     otherwise(
                         __ -> $(
                                 caseof(Skip.class, 
                                     ___ -> this::cfgEdgeSkipFirstRule),
                                 caseof(Io.class, 
                                     ___ -> cfgEdge.matchFor(s.s2)),
                                 otherwise(
                                     ___ -> this::cfgEdgeRule)
                                )
                                 .matchFor(s.s1))
                    )
                     .matchFor(s.s2)),
          caseof(IoUnion.class,
              s -> $(
                     caseof(Io.class, 
                         __ -> ioUnion.matchFor(s.s2)),
                     otherwise(
                         __ -> this::ioUnionRule)
                   )
                    .matchFor(s.s1)),
          caseof(Fed.class,
              __ -> this::fedRule
          )
      );
      // @formatter:on

  public Program small(final Program program) {
    final Stmt stmt = program.s;
    final Function<Program, Program> rule = pm.matchFor(stmt);
    final Program result = rule.apply(program);
    return result;
  }

  private Program edgeRule(final Program program) {
    printRule("edgeRule");
    final EdgeStmt s = (EdgeStmt) program.s;
    final Program p = small(program.cloneWithStmt(s.s));
    p.s = new EdgeStmt(s.t, s.v, p.s);
    return p;
  }

  private Program edgeSkipRule(final Program program) {
    printRule("edgeSkipRule");
    final EdgeStmt s = (EdgeStmt) program.s;
    for (final Vertex ve : program.Ve) {
      program.sdg.addEdge(s.v, ve, s.t);
    }
    for (final Vertex vc : program.Vc) {
      program.sdg.addEdge(s.v, vc, s.t);
    }
    program.Ve = new HashSet<>();
    program.Vc = new HashSet<>();
    program.s = new Skip();
    return program;
  }

  private Program edgeSeqRule(final Program program) {
    printRule("edgeSeqRule");
    final EdgeStmt s = (EdgeStmt) program.s;
    final Seq seq = (Seq) s.s;
    program.s = new Seq(new EdgeStmt(s.t, s.v, seq.s1), new EdgeStmt(s.t, s.v, seq.s2));
    return program;
  }

  private Program compilationUnitRule(final Program program) {
    printRule("compilationUnitRule");
    final CUnit s = (CUnit) program.s;
    final Vertex v = vertexCreator.compilationUnitVertex(s);
    program.sdg.addVertex(v);
    program.s = new EdgeStmt(EdgeType.MEMBER_OF, v, s.s);
    return program;
  }

  private Program packageRule(final Program program) {
    printRule("pkgRule");
    final Pkg s = (Pkg) program.s;
    final Vertex v = vertexCreator.packageVertex(s);
    program.sdg.addVertex(v);
    program.Ve.add(v);
    program.s = new Skip();
    return program;
  }

  private Program importRule(final Program program) {
    printRule("importRule");
    final Import s = (Import) program.s;
    final Vertex v = vertexCreator.importVertex(s);
    program.sdg.addVertex(v);
    program.Ve.add(v);
    program.s = new Skip();
    return program;
  }

  private Program classRule(final Program program) {
    printRule("classRule");
    final Cls s = (Cls) program.s;
    final Vertex v = vertexCreator.classVertex(s);
    program.sdg.addVertex(v);
    final EdgeStmt edge = new EdgeStmt(EdgeType.MEMBER_OF, v, s.s);
    program.s = new Seq(edge, new Ve(v));
    return program;
  }

  private Program defRule(final Program program) {
    printRule("defRule");
    final Def s = (Def) program.s;
    final Vertex v = vertexCreator.defVertex(s);
    v.setStartLine(s.startLine);
    v.setEndLine(s.endLine);
    program.sdg.addVertex(v);
    program.cfg.addVertex(v);
    program.Ve.add(v);
    final Param param = new Param(s.methodName, VertexType.FORMAL_IN, s.p);
    final Seq seq1 = new Seq(param, s.s);
    final CtrlEdge ctrlEdge = new CtrlEdge(true, v, seq1);
    final Io io = new Io(set(v), set(v));
    program.s = new Seq(new CfgEdge(io, ctrlEdge), new Fed(s.x));
    mEntryVertices.put(s.methodName, v);
    return program;
  }

  private Program voidDefRule(final Program program) {
    printRule("voidDefRule");
    final Def s = (Def) program.s;
    final Vertex v = vertexCreator.defVertex(s);
    v.setStartLine(s.startLine);
    v.setEndLine(s.endLine);
    program.sdg.addVertex(v);
    program.cfg.addVertex(v);
    program.Ve.add(v);
    final Param param = new Param(s.methodName, VertexType.FORMAL_IN, s.p);
    final Seq seq1 = new Seq(param, s.s);
    final CtrlEdge ctrlEdge = new CtrlEdge(true, v, seq1);
    final Io io = new Io(set(v), set(v));
    program.s = new Seq(new CfgEdge(io, ctrlEdge), new Fed(s.x));
    mEntryVertices.put(s.methodName, v);
    return program;
  }

  private Program seqRule(final Program program) {
    printRule("seqRule");
    final Seq seq = (Seq) program.s;
    final Program p = small(program.cloneWithStmt(seq.s1));
    p.s = new Seq(p.s, seq.s2);
    return p;
  }

  private Program seqSkipRule(final Program program) {
    printRule("seqSkipRule");
    final Seq s = (Seq) program.s;
    program.s = s.s2;
    return program;
  }

  private Program declRule(final Program program) {
    printRule("declRule");
    final Decl s = (Decl) program.s;
    final Vertex v = vertexCreator.declVertex(s);
    program.sdg.addVertex(v);
    program.Vc.add(v);
    program.s = new Skip();
    return program;
  }

  private Program assignRule(final Program program) {
    printRule("assignRule");
    final Assign assign = (Assign) program.s;
    final Vertex v = vertexCreator.assignVertex(assign);
    v.setAssignedVariable(assign.getDef());
    v.setReadingVariables(assign.getUses());
    program.sdg.addVertex(v);
    program.cfg.addVertex(v);
    program.Vc.add(v);
    program.s = new Io(set(v), set(v));
    return program;
  }

  private Program preOpRule(final Program program) {
    printRule("preOpRule");
    final PreOp preOp = (PreOp) program.s;
    final Vertex v = vertexCreator.preOpVertex(preOp);
    v.setAssignedVariable(preOp.getDef());
    v.setReadingVariables(preOp.getUses());
    program.sdg.addVertex(v);
    program.cfg.addVertex(v);
    program.Vc.add(v);
    program.s = new Io(set(v), set(v));
    return program;
  }

  private Program postOpRule(final Program program) {
    printRule("postOpRule");
    final PostOp postOp = (PostOp) program.s;
    final Vertex v = vertexCreator.postOpVertex(postOp);
    v.setAssignedVariable(postOp.getDef());
    v.setReadingVariables(postOp.getUses());
    program.sdg.addVertex(v);
    program.cfg.addVertex(v);
    program.Vc.add(v);
    program.s = new Io(set(v), set(v));
    return program;
  }

  private Program returnRule(final Program program) {
    printRule("returnRule");
    final Return ret = (Return) program.s;
    final Vertex v = vertexCreator.returnVertex(ret);
    v.setReadingVariables(ret.getUses());
    program.sdg.addVertex(v);
    program.cfg.addVertex(v);
    program.Vc.add(v);
    program.s = new Io(set(v), new HashSet<>());
    return program;
  }

  private Program vcRule(final Program program) {
    printRule("vcRule");
    final Vc vc = (Vc) program.s;
    program.Vc.add(vc.v);
    program.s = new Skip();
    return program;
  }

  private Program veRule(final Program program) {
    printRule("veRule");
    final Ve ve = (Ve) program.s;
    program.Ve.add(ve.v);
    program.s = new Skip();
    return program;
  }

  private Program ctrlEdgeRule(final Program program) {
    printRule("ctrlEdgeRule");
    final CtrlEdge ctrledge = (CtrlEdge) program.s;
    final Stmt s = ctrledge.s;
    final Program p = small(program.cloneWithStmt(s));
    p.s = new CtrlEdge(ctrledge.B, ctrledge.N, p.s);
    return p;
  }

  private Program ctrlEdgeSkipRule(final Program program) {
    printRule("ctrlEdgeSkipRule");
    final CtrlEdge s = (CtrlEdge) program.s;
    createEdges(program.sdg, s.B, s.N, program.Vc);
    program.Vc = new HashSet<>();
    program.s = new Skip();
    return program;
  }

  private Program ctrlEdgeSeqRule(final Program program) {
    printRule("ctrlEdgeSeqRule");
    final CtrlEdge s = (CtrlEdge) program.s;
    final Seq seq = (Seq) s.s;
    program.s = new Seq(new CtrlEdge(s.B, s.N, seq.s1), new CtrlEdge(s.B, s.N, seq.s2));
    return program;
  }

  private Program ifThenElseRule(final Program program) {
    printRule("ifThenElseRule");
    final IfThenElse s = (IfThenElse) program.s;
    final Vertex v = vertexCreator.ifThenElseVertex(s);
    v.setAssignedVariable(s.getDef());
    v.setReadingVariables(s.getUses());
    program.sdg.addVertex(v);
    program.cfg.addVertex(v);
    program.Vc = new HashSet<>();
    final CtrlEdge ctrl1 = new CtrlEdge(true, v, s.s1);
    final CtrlEdge ctrl2 = new CtrlEdge(false, v, s.s2);
    final Io io = new Io(set(v), set(v));
    final CfgEdge cfgEdge1 = new CfgEdge(io, ctrl1);
    final CfgEdge cfgEdge2 = new CfgEdge(io, ctrl2);
    final IoUnion outUnion = new IoUnion(cfgEdge1, cfgEdge2);
    program.s = new Seq(outUnion, new Vc(v));
    return program;
  }

  private Program whileRule(final Program program) {
    printRule("whileRule");
    final While s = (While) program.s;
    final Vertex v = vertexCreator.whileVertex(s);
    v.setAssignedVariable(s.getDef());
    v.setReadingVariables(s.getUses());
    program.sdg.addVertex(v);
    program.sdg.addEdge(v, v, EdgeType.CTRL_TRUE);
    program.cfg.addVertex(v);
    program.Vc = new HashSet<>();
    final CtrlEdge ctrl = new CtrlEdge(true, v, s.s);
    final Io io = new Io(set(v), set(v));
    final CfgEdge cfgEdge1 = new CfgEdge(io, ctrl);
    final CfgEdge cfgEdge2 = new CfgEdge(cfgEdge1, io);
    program.s = new Seq(cfgEdge2, new Vc(v));
    return program;
  }

  private Program doWhileRule(final Program program) {
    printRule("doWhileRule");
    final DoWhile s = (DoWhile) program.s;
    final Vertex v = vertexCreator.doWhileVertex(s);
    v.setAssignedVariable(s.getDef());
    v.setReadingVariables(s.getUses());
    program.sdg.addVertex(v);
    program.sdg.addEdge(v, v, EdgeType.CTRL_TRUE);
    program.cfg.addVertex(v);
    program.Vc = new HashSet<>();
    final CtrlEdge ctrl = new CtrlEdge(true, v, s.s);
    final Io io1 = new Io(set(v), set(v));
    final Io io2 = new Io(null, set(v));
    final CfgEdge cfgEdge1 = new CfgEdge(ctrl, io1);
    final CfgEdge cfgEdge2 = new CfgEdge(cfgEdge1, io2);
    program.s = new Seq(cfgEdge2, new Vc(v));
    return program;
  }

  private Program ctrlEdgeDoWhileRule(final Program program) {
    printRule("ctrlEdgeDoWhileRule");
    final CtrlEdge s = (CtrlEdge) program.s;
    final DoWhile doWhile = (DoWhile) s.s;
    final Vertex v = vertexCreator.doWhileVertex(doWhile);
    v.setAssignedVariable(doWhile.getDef());
    v.setReadingVariables(doWhile.getUses());
    program.sdg.addVertex(v);
    program.cfg.addVertex(v);
    program.Vc = new HashSet<>();
    final List<Boolean> B = list(s.B, true);
    final List<Vertex> N = list(s.N, v);
    final CtrlEdge ctrl = new CtrlEdge(B, N, new Seq(doWhile.s, new Vc(v)));
    final Io io1 = new Io(set(v), set(v));
    final Io io2 = new Io(null, set(v));
    final CfgEdge cfgEdge1 = new CfgEdge(ctrl, io1);
    program.s = new CfgEdge(cfgEdge1, io2);
    return program;
  }

  private Program forRule(final Program program) {
    printRule("forRule");
    final For s = (For) program.s;
    final Vertex v = vertexCreator.forVertex(s);
    v.setAssignedVariable(s.sc.getDef());
    v.setReadingVariables(s.sc.getUses());
    program.sdg.addVertex(v);
    program.cfg.addVertex(v);
    program.sdg.addEdge(v, v, EdgeType.CTRL_TRUE);
    program.Vc = new HashSet<>();
    final CtrlEdge ctrlEdge = new CtrlEdge(true, v, new Seq(s.s, s.su));
    final Io io = new Io(set(v), set(v));
    final CfgEdge cfgEdge2 = new CfgEdge(io, ctrlEdge);
    final Io io2 = new Io(set(v), null);
    final CfgEdge cfgEdge1 = new CfgEdge(cfgEdge2, io2);
    final Io io1 = new Io(new HashSet<>(), set(v));
    final IoUnion ioUnion = new IoUnion(io1, cfgEdge1);
    program.s = new Seq(s.si, new Seq(ioUnion, new Vc(v)));
    return program;
  }

  private Program switchEmptyRule(final Program program) {
    printRule("switchEmptyRule");
    final Switch s = (Switch) program.s;
    final Vertex v = new Vertex(VertexType.CTRL, s.e.toString());
    vertexCreator.setId(v);
    program.sdg.addVertex(v);
    program.Vc.add(v);
    program.s = new Skip();
    return program;
  }

  private Program switchCaseRule(final Program program) {
    printRule("switchCaseRule");
    final Switch s = (Switch) program.s;
    final SingleSwitch ss = (SingleSwitch) s.sb;
    final String x = largeCsCond(s.e, ss.cs);
    final IfThenElse ifThenElse = new IfThenElse(new Str(x), ss.s, new Skip());
    ifThenElse.setUses(s.getUses());
    program.s = ifThenElse;
    return program;
  }

  private Program switchCasesRule(final Program program) {
    printRule("switchCasesRule");
    final Switch s = (Switch) program.s;
    final MultiSwitch sb = (MultiSwitch) s.sb;
    final Switch sw = new Switch(s.e, sb.ss);
    final Program p = small(program.cloneWithStmt(sw));
    p.s = new Switch(s.e, sb.sb);
    return p;
  }

  private Program breakRule(final Program program) {
    printRule("breakRule");
    final Break s = (Break) program.s;
    final Vertex v = vertexCreator.breakVertex(s);
    program.sdg.addVertex(v);
    program.Vc.add(v);
    program.s = new Skip();
    return program;
  }

  private Program continueRule(final Program program) {
    printRule("continueRule");
    final Continue s = (Continue) program.s;
    final Vertex v = vertexCreator.continueVertex(s);
    program.sdg.addVertex(v);
    program.Vc.add(v);
    program.s = new Skip();
    return program;
  }

  private Program paramActualInRule(final Program program) {
    printRule("paramActualInRule");
    final Param param = (Param) program.s;
    final LinkedHashSet<Vertex> Vp = largeParam(param);
    for (final Vertex v : Vp) {
      program.sdg.addVertex(v);
      program.Vc.add(v);
    }
    program.sd.add(new ParamIn(param.x, Vp));
    program.s = new Skip();
    return program;
  }

  private Program paramActualOutRule(final Program program) {
    printRule("paramActualOutRule");
    final Param param = (Param) program.s;
    final Set<Vertex> Vp = largeParam(param);
    for (final Vertex v : Vp) {
      program.sdg.addVertex(v);
      program.Vc.add(v);
    }
    final Vertex Vp0 = Vp.iterator().next();
    program.sd.add(new ParamOut(param.x, Vp0));
    program.s = new Skip();
    return program;
  }

  private Program paramFormalInRule(final Program program) {
    printRule("paramFormalInRule");
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
    final int n = Vp.size();
    final List<Stmt> s = new LinkedList<>();
    final Iterator<Vertex> it = Vp.iterator();
    for (int i = 0; i < n; i++) {
      final Vertex v = it.next();
      s.add(new Io(set(v), set(v)));
      program.cfg.addVertex(v);
    }
    program.s = Translator.seq(s);
    return program;
  }

  private Program paramFormalOutRule(final Program program) {
    printRule("paramFormalOutRule");
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
    program.s = new Skip();
    return program;
  }

  private Program paramInRule(final Program program) {
    printRule("paramInRule");
    final ParamIn paramIn = (ParamIn) program.s;
    final LinkedHashSet<Vertex> Px = program.P.get(paramIn.x);
    if (Px != null && !Px.isEmpty() && Px.size() >= paramIn.V.size()) {
      for (int i = 0; i < paramIn.V.size(); i++) {
        final Vertex v = vtxAtIdx(Px, i);
        final Vertex Vi = vtxAtIdx(paramIn.V, i);
        program.sdg.addEdge(Vi, v, EdgeType.PARAM_IN);
      }
    } else if (PRINT)
      System.out.println("[WARN] Cannot find parameter vertices for method '" + paramIn.x + "'.");
    program.s = new Skip();
    return program;
  }

  private Program paramOutRule(final Program program) {
    printRule("paramOutRule");
    final ParamOut paramOut = (ParamOut) program.s;
    final LinkedHashSet<Vertex> Px = program.P.get(paramOut.x);
    if (Px != null && !Px.isEmpty()) {
      final Vertex Pxm1 = vtxAtIdx(Px, Px.size() - 1);
      if (VertexType.FORMAL_OUT.equals(Pxm1.getType())) {
        program.sdg.addEdge(Pxm1, paramOut.v, EdgeType.PARAM_OUT);
      } else if (PRINT)
        System.out.println(
            "[WARN] " + Pxm1 + " type is '" + Pxm1.getType() + "'. Expected was 'FORMAL_OUT'.");
    } else if (PRINT)
      System.out.println("[WARN] Cannot find parameter vertices for method '" + paramOut.x + "'.");
    program.s = new Skip();
    return program;
  }

  private Program callRule(final Program program) {
    printRule("callRule");
    final Call s = (Call) program.s;
    final String methodName = Translator.removeClassName(s.x);
    final Vertex vc = vertexCreator.callVertex(s);
    vc.setAssignedVariable(s.getDef());
    vc.setReadingVariables(s.getUses());
    program.sdg.addVertex(vc);
    program.cfg.addVertex(vc);
    program.sd.add(new CallEdge(vc, methodName));
    final Io io = new Io(set(vc), set(vc));
    final Seq seq2 = new Seq(new Vc(vc), io);
    final Param param = new Param(methodName, VertexType.ACTUAL_IN, s.p);
    program.s = new Seq(new CtrlEdge(true, vc, param), seq2);
    return program;
  }

  private Program assignCallRule(final Program program) {
    printRule("assignCallRule");
    final Assign assign = (Assign) program.s;
    final Call call = (Call) assign.e;
    final String methodName = Translator.removeClassName(call.x);
    final Vertex va = vertexCreator.assignCallVertex(assign);
    va.setAssignedVariable(assign.getDef());
    va.setReadingVariables(call.getUses());
    program.sdg.addVertex(va);
    program.cfg.addVertex(va);
    program.sd.add(new CallEdge(va, methodName));
    final Param param = new Param(methodName, VertexType.ACTUAL_IN, call.p);
    final Io io = new Io(set(va), set(va));
    final Seq seq3 = new Seq(new Vc(va), io);
    program.s = new Seq(new CtrlEdge(true, va, param), seq3);
    return program;
  }

  private Program callEdgeRule(final Program program) {
    printRule("callEdgeRule");
    final CallEdge callEdge = (CallEdge) program.s;
    final Vertex ve = mEntryVertices.get(callEdge.x);
    if (ve == null) {
      if (PRINT)
        System.out.println("[WARN] Cannot find ENTER vertex for method '" + callEdge.x + "'");
    } else {
      program.sdg.addEdge(callEdge.v, ve, EdgeType.CALL);
      vertexCreator.selfCall(callEdge.v);
    }
    program.s = new Skip();
    return program;
  }

  // Control-flow graph

  private Program ioRule(final Program program) {
    printRule("ioRule");
    program.s = new Skip();
    return program;
  }

  private Program seqIoRule(final Program program) {
    printRule("seqIoRule");
    final Stmt s1 = ((Seq) program.s).s1;
    final Stmt s2 = ((Seq) program.s).s2;
    program.s = new CfgEdge(s1, s2);
    return program;
  }

  private Program ctrlEdgeIoRule(final Program program) {
    printRule("ctrlEdgeIoRule");
    final CtrlEdge s = (CtrlEdge) program.s;
    createEdges(program.sdg, s.B, s.N, program.Vc);
    program.Vc = new HashSet<>();
    program.s = s.s;
    return program;
  }

  private Program cfgEdgeRule(final Program program) {
    printRule("cfgEdgeRule");
    final CfgEdge cfgEdge = (CfgEdge) program.s;
    final Stmt s = cfgEdge.s1;
    final Program p = small(program.cloneWithStmt(s));
    p.s = new CfgEdge(p.s, cfgEdge.s2);
    return p;
  }

  private Program cfgEdgeIoRule(final Program program) {
    printRule("cfgEdgeIoRule");
    final CfgEdge cfgEdge = (CfgEdge) program.s;
    final Stmt s = cfgEdge.s2;
    final Program p = small(program.cloneWithStmt(s));
    p.s = new CfgEdge(cfgEdge.s1, p.s);
    return p;
  }

  private Program cfgEdgeSkipFirstRule(final Program program) {
    printRule("cfgEdgeSkipFirstRule");
    final CfgEdge cfgEdge = (CfgEdge) program.s;
    program.s = cfgEdge.s2;
    return program;
  }

  private Program cfgEdgeSkipSecondRule(final Program program) {
    printRule("cfgEdgeSkipSecondRule");
    final CfgEdge cfgEdge = (CfgEdge) program.s;
    program.s = cfgEdge.s1;
    return program;
  }

  private Program cfgEdgeIoEndDefRule(final Program program) {
    printRule("cfgEdgeIoEndDefRule");
    final CfgEdge cfgEdge = (CfgEdge) program.s;
    program.s = cfgEdge.s2;
    return program;
  }

  private Program cfgEdgeIoCopyInRule(final Program program) {
    printRule("cfgEdgeIoCopyInRule");
    final CfgEdge cfgEdge = (CfgEdge) program.s;
    final Io io1 = (Io) cfgEdge.s1;
    final Io io2 = (Io) cfgEdge.s2;
    io2.I = new HashSet<>(io1.I);
    program.s = new CfgEdge(io1, io2);
    return program;
  }

  private Program cfgEdgeIoCopyOutRule(final Program program) {
    printRule("cfgEdgeIoCopyOutRule");
    final CfgEdge cfgEdge = (CfgEdge) program.s;
    final Io io1 = (Io) cfgEdge.s1;
    final Io io2 = (Io) cfgEdge.s2;
    io2.O = new HashSet<>(io1.O);
    program.s = new CfgEdge(io1, io2);
    return program;
  }

  private Program cfgEdgeIoIoRule(final Program program) {
    printRule("cfgEdgeIoIoRule");
    final CfgEdge cfgEdge = (CfgEdge) program.s;
    final Io io1 = (Io) cfgEdge.s1;
    final Io io2 = (Io) cfgEdge.s2;
    for (final Vertex o : io1.O) {
      for (final Vertex i : io2.I) {
        program.cfg.addEdge(o, i, new Edge(o, i));
      }
    }
    program.s = new Io(io1.I, io2.O);
    return program;
  }

  private Program ioUnionRule(final Program program) {
    printRule("ioUnionRule");
    final IoUnion outUnion = (IoUnion) program.s;
    final Stmt s1 = outUnion.s1;
    final Stmt s2 = outUnion.s2;
    final Program p = small(program.cloneWithStmt(s1));
    p.s = new IoUnion(p.s, s2);
    return p;
  }

  private Program ioUnionIoRule(final Program program) {
    printRule("ioUnionIoRule");
    final IoUnion outUnion = (IoUnion) program.s;
    final Io io = (Io) outUnion.s1;
    final Stmt s = outUnion.s2;
    final Program p = small(program.cloneWithStmt(s));
    p.s = new IoUnion(io, p.s);
    return p;
  }

  private Program ioUnionIoIoRule(final Program program) {
    printRule("ioUnionIoIoRule");
    final IoUnion outUnion = (IoUnion) program.s;
    final Io s1 = (Io) outUnion.s1;
    final Io s2 = (Io) outUnion.s2;
    final Set<Vertex> I = union(s1.I, s2.I);
    final Set<Vertex> O = union(s1.O, s2.O);
    program.s = new Io(I, O);
    return program;
  }

  private Program fedRule(final Program program) {
    printRule("fedRule");
    final Fed fed = (Fed) program.s;
    program.F.put(fed.x, program.clonedCfg());
    program.cfg = new DefaultDirectedGraph<Vertex, Edge>(Edge.class);
    program.s = new Skip();
    return program;
  }

  // Large-step

  private LinkedHashSet<Vertex> largeParam(final Param param) {
    LinkedHashSet<Vertex> result = null;
    final edu.rit.goal.sdg.interpreter.params.Param p =
        (edu.rit.goal.sdg.interpreter.params.Param) param.p;
    if (p instanceof EmptyParam) {
      result = largeNoParamRule(param);
    } else if (p instanceof Str) {
      result = largeParamRule(param);
    } else if (p instanceof Params) {
      result = largeParamsRule(param);
    }
    return result;
  }

  private LinkedHashSet<Vertex> largeNoParamRule(final Param param) {
    return new LinkedHashSet<>();
  }

  private LinkedHashSet<Vertex> largeParamRule(final Param param) {
    final Str str = (Str) param.p;
    final LinkedHashSet<Vertex> V = new LinkedHashSet<>();
    final Vertex v = new Vertex(param.t, str.value);
    vertexCreator.setId(v);
    // Def and uses for data dependences
    v.setAssignedVariable(str.getDef());
    v.setReadingVariables(str.getUses());
    DefUsesUtils.paramsDefUses(v, str.value);
    V.add(v);
    return V;
  }

  private LinkedHashSet<Vertex> largeParamsRule(final Param param) {
    final Params params = (Params) param.p;
    final LinkedHashSet<Vertex> V = largeParam(new Param(param.t, params.p));
    final Vertex v = new Vertex(param.t, params.x);
    vertexCreator.setId(v);
    // Def and uses for data dependences
    v.setAssignedVariable(params.getDef());
    v.setReadingVariables(params.getUses());
    DefUsesUtils.paramsDefUses(v, params.x);
    final LinkedHashSet<Vertex> result = new LinkedHashSet<>();
    result.add(v);
    result.addAll(V);
    return result;
  }

  private String largeCsCond(final Expr e, final ICase cs) {
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

  private String csCondCaseRule(final Expr e, final ICase cs) {
    final SingleCase s = (SingleCase) cs;
    return e + "==" + s.x;
  }

  private String csCondCasesRule(final Expr e, final ICase cs) {
    final MultiCase s = (MultiCase) cs;
    final String x1 = largeCsCond(e, s.x);
    final String x2 = largeCsCond(e, s.cs);
    return x1 + "||" + x2;
  }

  // Helper methods

  private void createEdges(final SysDepGraph sdg, final List<Boolean> B, final List<Vertex> sources,
      final Set<Vertex> targets) {
    if (targets.isEmpty())
      return;
    final int bSize = B.size();
    if (bSize != sources.size())
      throw new IllegalArgumentException("B and sources should be the same size.");
    for (int i = 0; i < bSize; i++) {
      final Boolean b = B.get(i);
      final EdgeType type = b ? EdgeType.CTRL_TRUE : EdgeType.CTRL_FALSE;
      final Vertex s = sources.get(i);
      for (final Vertex t : targets) {
        sdg.addEdge(s, t, type);
      }
    }
  }

  private Vertex vtxAtIdx(final LinkedHashSet<Vertex> V, int idx) {
    final Iterator<Vertex> it = V.iterator();
    while (idx-- > 0)
      it.next();
    return it.next();
  }

  private void printRule(final String rule) {
    if (PRINT && PRINT_RULES)
      System.out.println(rule);
  }

  private Set<Vertex> set(final Vertex v) {
    final Set<Vertex> result = new HashSet<>();
    result.add(v);
    return result;
  }

  private Set<Vertex> union(final Set<Vertex> s1, final Set<Vertex> s2) {
    final Set<Vertex> result = new HashSet<>(s1);
    result.addAll(s2);
    return result;
  }

  private <T> List<T> list(final List<T> l, final T element) {
    final List<T> result = new LinkedList<>(l);
    result.add(element);
    return result;
  }

}
