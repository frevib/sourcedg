package edu.rit.goal.sdg.interpreter.pattern;

import java.util.function.Function;
import edu.rit.goal.sdg.graph.VertexType;
import edu.rit.goal.sdg.interpreter.Program;

public class VertexTypePattern implements Pattern {
  private final VertexType pattern;
  private final Function<VertexType, Function<Program, Program>> function;

  public VertexTypePattern(final VertexType pattern,
      final Function<VertexType, Function<Program, Program>> function) {
    this.pattern = pattern;
    this.function = function;
  }

  @Override
  public boolean matches(final Object value) {
    return pattern.equals(value);
  }

  @Override
  public Function<Program, Program> apply(final Object value) {
    return function.apply((VertexType) value);
  }

  public static Pattern caseof(final VertexType pattern,
      final Function<VertexType, Function<Program, Program>> function) {
    return new VertexTypePattern(pattern, function);
  }

}