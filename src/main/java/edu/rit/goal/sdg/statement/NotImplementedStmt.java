package edu.rit.goal.sdg.statement;

import org.antlr.v4.runtime.ParserRuleContext;

public class NotImplementedStmt implements Stmt {

    private final String msg;

    public NotImplementedStmt(final Object cls, final ParserRuleContext ctx) {
	msg = cls.getClass().getSimpleName() + "[" + ctx.getClass().getSimpleName() + "]";
    }

    public String getMsg() {
	return msg;
    }

    @Override
    public String toString() {
	return msg;
    }

}