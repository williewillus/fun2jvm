package vlee12.parser;

import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static vlee12.parser.Parser.TokenType.*;


public class Parser {

    enum TokenType {
        NONE,
        INT,
        ID,
        END,
        LPAREN,
        RPAREN,
        PLUS,
        MUL,
        LBRACE,
        RBRACE,
        EQ,
        IF,
        ELSE,
        SEMI,
        WHILE,
        DEQUALS,
        LT,
        GT,
        NE,
        PRINT,
        FUN,
        COMMA,
        RETURN
    }

    public static List<Fun> parse(BufferedReader program) {
        return new Parser(program.lines().collect(Collectors.joining("\n")).toCharArray()).funs();
    }
    
    private TokenType curToken = TokenType.NONE;
    private final char[] buf;
    private int curIndex = -1;
    private int toAdvance = 1;
    private int line = 0;
    private int pos = -1;
    private String curIdentifier = "";
    private int curValue_unsigned = 0;

    private Parser(char[] program) {
        this.buf = program;
    }

    private void error() {
        System.err.printf("===> error at %d:%d <===%n", line, pos);
        throw new RuntimeException();
    }

    private boolean shouldSkip(char c) {
        return c == ' ' || c == '\n' || c == '\t';
    }

    private void advanceToken() {
        curIndex += toAdvance;
        pos += toAdvance;
        toAdvance = 1;

        // Advance through all spaces, newlines, and tabs
        while (curIndex < buf.length && shouldSkip(buf[curIndex])) {
            if (buf[curIndex] == '\n') {
                line++;
                pos = 0;
            }
            curIndex += 1;
        }

        if (curIndex >= buf.length) {
            curToken = END;
        } else {
            char c = buf[curIndex];
            if (c >= 'a' && c <= 'z') {
                curIdentifier = "";

                curIdentifier += c;
                int counter = curIndex + toAdvance;
                while (counter < buf.length - 1 &&
                        ((buf[counter] >= 'a' && buf[counter] <= 'z') || (buf[counter] >= '0' && buf[counter] <= '9'))) {

                    curIdentifier += buf[counter]; // append next character
                    toAdvance++; // update character count
                    counter = curIndex + toAdvance;
                }


                // Check for while, if, and else - exact matches. These are not greedy.
                switch (curIdentifier) {
                    case "while": curToken = WHILE; break;
                    case "if": curToken = IF; break;
                    case "else": curToken = ELSE; break;
                    case "print": curToken = PRINT; break;
                    case "fun": curToken = FUN; break;
                    case "return": curToken = RETURN; break;
                    default: curToken = ID; break;
                }
            } else if (c >= '0' && c <= '9') {
                String a = "" + c;
                int counter = curIndex + toAdvance;
                while (counter < buf.length - 1 && ((buf[counter] >= '0' && buf[counter] <= '9') || buf[counter] == '_')) {
                    if (buf[counter] != '_') {
                        a += (buf[counter] - '0');
                    }

                    toAdvance++;
                    counter = curIndex + toAdvance;
                }

                curValue_unsigned = Integer.parseUnsignedInt(a);
                curToken = INT;
            } else {
                switch (c) {
                    case '*': curToken = MUL; break;
                    case '+': curToken = PLUS; break;
                    case '=': {
                        if (curIndex < buf.length - 2 && buf[curIndex + 1] == '=') {
                            curToken = DEQUALS;
                            toAdvance++;
                        } else {
                            curToken = EQ;
                        }
                        break;
                    }
                    case '{': curToken = LBRACE; break;
                    case '}': curToken = RBRACE; break;
                    case '(': curToken = LPAREN; break;
                    case ')': curToken = RPAREN; break;
                    case ';': curToken = SEMI; break;
                    case ',': curToken = COMMA; break;
                    case '>': curToken = GT; break;
                    case '<': {
                        if (curIndex < buf.length - 2 && buf[curIndex + 1] == '>') {
                            curToken = NE;
                            toAdvance++;
                        } else {
                            curToken = LT;
                        }
                        break;
                    }
                    default: {
                        System.err.printf("undefined char %c%n", c);
                        error();
                    }
                }
            }
        }
    }

    private List<Fun> funs() {
        advanceToken(); // Get the first token
        List<Fun> ret = new ArrayList<>();
        while (curToken != END) {
            ret.add(fun());
        }
        return ret;
    }

    private Fun fun() {
        if (curToken != FUN)
            error();
        advanceToken();
        if (curToken != ID)
            error();

        String name = curIdentifier;
        advanceToken();
        return new Fun(name, formals(), statement());
    }

    private List<String> formals() {
        if (curToken != LPAREN)
            error();
        List<String> ret = new ArrayList<>();
        advanceToken();
        while (curToken == ID || curToken == COMMA) {
            if (curToken == ID) {
                ret.add(curIdentifier);
            }
            advanceToken(); // todo syntax is a bit lax here
        }
        if (curToken != RPAREN)
            error();
        advanceToken();
        return ret;
    }

    private Statement statement() {
        switch (curToken) {
            case ID: {
                String assignName = curIdentifier;
                advanceToken();
                if (curToken != EQ)
                    error();
                advanceToken();
                Expression assignValue = expression();
                if (curToken == SEMI)
                    advanceToken();
                return new Statement.Assign(assignName, assignValue);
            }
            case RETURN: {
                advanceToken();
                return new Statement.Return(expression());
            }
            case LBRACE: {
                advanceToken();
                List<Statement> ret = block();
                if (curToken != RBRACE)
                    error();
                advanceToken();
                return new Statement.Block(ret);
            }
            case PRINT: {
                advanceToken();
                Expression val = expression();
                return new Statement.Print(val);
            }
            case IF: {
                advanceToken();
                Expression cond = expression();
                Statement truth = statement();
                Statement other = null;
                if (curToken == ELSE) {
                    advanceToken();
                    other = statement();
                }
                return new Statement.If(cond, truth, other);
            }
            case WHILE: advanceToken(); return new Statement.While(expression(), statement());
            case SEMI: advanceToken(); return new Statement.Block(Collections.emptyList());
            default: return null;
        }
    }

    private List<Statement> block() {
        List<Statement> p = new ArrayList<>();

        Statement first = statement();
        if (first != null) {
            p.add(first);
            p.addAll(block());
        }

        return p;
    }

    private Expression expression() {
        return e4();
    }

    private Expression e4() {
        Expression left = e3();
        switch (curToken) {
            case LT: advanceToken(); return new Expression.BinaryExpr(ExpressionType.LT, left, e4());
            case GT: advanceToken(); return new Expression.BinaryExpr(ExpressionType.GT, left, e4());
            case NE: advanceToken(); return new Expression.BinaryExpr(ExpressionType.NE, left, e4());
            case DEQUALS: advanceToken(); return new Expression.BinaryExpr(ExpressionType.EQ, left, e4());
            default: return left;
        }
    }

    private Expression e3() {
        Expression left = e2();
        switch (curToken) {
            case PLUS: advanceToken(); return new Expression.BinaryExpr(ExpressionType.PLUS, left, e3());
            default: return left;
        }
    }

    private Expression e2() {
        Expression left = e1();
        switch (curToken) {
            case MUL: advanceToken(); return new Expression.BinaryExpr(ExpressionType.MUL, left, e2());
            default: return left;
        }
    }

    private Expression e1() {
        if (curToken == LPAREN) {
            advanceToken();
            Expression e = expression();
            if (curToken != RPAREN)
                error();
            advanceToken();
            return e;
        } else if (curToken == INT) {
            Expression ret = new Expression.Val(curValue_unsigned);
            advanceToken();
            return ret;
        } else if (curToken == ID) {
            String id = curIdentifier;
            advanceToken();
            if (curToken == LPAREN) {
                advanceToken();

                Expression e = new Expression.Call(id, actuals());

                if (curToken != RPAREN)
                    error();
                advanceToken();

                return e;
            } else {
                return new Expression.Var(id);
            }
        } else {
            error();
            return null;
        }
    }

    private List<Expression> actuals() {
        if (curToken == RPAREN)
            return Collections.emptyList();
        List<Expression> p = new ArrayList<>();
        p.add(expression());

        if (curToken == COMMA) {
            advanceToken();
            p.addAll(actuals());
        }

        return p;
    }
}
