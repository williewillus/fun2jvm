package vlee12.parser;

import java.util.List;

public class Expression {
    public final ExpressionType kind;

    private Expression(ExpressionType kind) {
        this.kind = kind;
    }

    public static class Var extends Expression {
        public final String varName;

        Var(String name) {
            super(ExpressionType.VAR);
            this.varName = name;
        }

        @Override
        public String toString() {
            return varName;
        }
    }

    public static class Val extends Expression {
        public final int value_unsigned;

        Val(int val) {
            super(ExpressionType.VAL);
            this.value_unsigned = val;
        }

        @Override
        public String toString() {
            return Integer.toUnsignedString(value_unsigned);
        }
    }

    public static class BinaryExpr extends Expression {
        public final Expression left;
        public final Expression right;

        BinaryExpr(ExpressionType kind, Expression left, Expression right) {
            super(kind);
            this.left = left;
            this.right = right;
        }

        @Override
        public String toString() {
            return "(" + getOp() + " " + left + " " + right + ")";
        }

        private String getOp() {
            switch (kind) {
                case PLUS: return "+";
                case MUL: return "*";
                case EQ: return "=";
                case LT: return "<";
                case GT: return ">";
                case NE: return "!=";
                default: return "?";
            }
        }
    }

    public static class Call extends Expression {
        public final String callName;
        public final List<Expression> callActuals;

        Call(String name, List<Expression> actuals) {
            super(ExpressionType.CALL);
            this.callName = name;
            this.callActuals = actuals;
        }

        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder("(").append(callName);
            for (Expression e : callActuals)
                builder.append(" ").append(e);
            builder.append(")");
            return builder.toString();
        }
    }
}
