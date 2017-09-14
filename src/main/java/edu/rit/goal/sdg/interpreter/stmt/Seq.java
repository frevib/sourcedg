package edu.rit.goal.sdg.interpreter.stmt;

public class Seq extends Stmt {

    public Stmt s1;
    public Stmt s2;

    public Seq(final Stmt s1, final Stmt s2) {
	super();
	this.s1 = s1;
	this.s2 = s2;
    }

    @Override
    public String toString() {
	return "{" + s1 + " ; " + s2 + "}";
    }

}