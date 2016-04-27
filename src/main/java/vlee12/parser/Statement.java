package vlee12.parser;

import java.util.List;
import java.util.stream.Collectors;

public class Statement {
    public final StatementType kind;

    private Statement(StatementType kind) {
        this.kind = kind;
    }

    public static class Assign extends Statement {
        public final String assignName;
        public final Expression assignValue;

        public Assign(String name, Expression val) {
            super(StatementType.ASSIGNMENT);
            this.assignName = name;
            this.assignValue = val;
        }

        @Override
        public String toString() {
            return "(let " + assignName + " " + assignValue + ")";
        }
    }

    public static class Print extends Statement {
        public final Expression printValue;

        public Print(Expression val) {
            super(StatementType.PRINT);
            this.printValue = val;
        }

        @Override
        public String toString() {
            return "(print " + printValue + ")";
        }
    }

    public static class If extends Statement {
        public final Expression ifCondition;
        public final Statement ifThen;
        public final Statement ifElse;

        public If(Expression cond, Statement truth, Statement other) {
            super(StatementType.IF);
            this.ifCondition = cond;
            this.ifThen = truth;
            this.ifElse = other;
        }

        @Override
        public String toString() {
            return "(if " + ifCondition + " " + ifThen + " " + ifElse + ")";
        }
    }

    public static class While extends Statement {
        public final Expression whileCondition;
        public final Statement whileBody;

        public While(Expression cond, Statement body) {
            super(StatementType.WHILE);
            this.whileCondition = cond;
            this.whileBody = body;
        }

        @Override
        public String toString() {
            return "(while " + whileCondition + " " + whileBody + ")";
        }
    }

    public static class Block extends Statement {
        public final List<Statement> block;

        public Block(List<Statement> block) {
            super(StatementType.BLOCK);
            this.block = block;
        }

        @Override
        public String toString() {
            return block.stream().map(Object::toString).collect(Collectors.joining(" "));
        }
    }

    public static class Return extends Statement {
        public final Expression returnValue;

        public Return(Expression val) {
            super(StatementType.RETURN);
            this.returnValue = val;
        }

        @Override
        public String toString() {
            return "(ret " + returnValue + ")";
        }
    }
}
