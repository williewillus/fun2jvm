package vlee12.compiler;

import vlee12.parser.Expression;
import vlee12.parser.ExpressionType;
import vlee12.parser.Fun;
import vlee12.parser.Statement;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static vlee12.parser.ExpressionType.EQ;
import static vlee12.parser.ExpressionType.GT;
import static vlee12.parser.ExpressionType.LT;
import static vlee12.parser.ExpressionType.NE;

public class Compiler {
    public static byte[] compile(List<Fun> funs, String className) {
        return new Compiler(funs, className).genHex();
    }

    private final List<Fun> funs;
    private final Set<String> globalVars = new HashSet<>();
    private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    private final List<ConstantPoolEntry> constantPoolEntries = new ArrayList<>();
    private final String className;

    private Compiler(List<Fun> funs, String name) {
        this.funs = funs;
        this.className = name;
    }

    /**
     * Finds the index of a constant pool entry equivalent to the one given,
     * or makes a new one and returns its index
     * Note that the index is +1 from expected, because the JVM indexes the Constant Pool using [1, size]
     */
    private short findOrPut(ConstantPoolEntry entry) {
        int index = constantPoolEntries.indexOf(entry);
        short ret = 0;
        if (index == -1) {
            constantPoolEntries.add(entry);
            ret = ((short) constantPoolEntries.size());
        } else {
            ret = ((short) (index + 1));
        }

        return ret;
    }

    // Variant of above for Class Constant Pool Entries
    private short getClassConstant(String name) {
        short entryIndex = findOrPut(new ConstantPoolEntry.Utf8(name));
        constantPoolEntries.add(new ConstantPoolEntry.Class(entryIndex));
        return ((short) constantPoolEntries.size());
    }

    // Variant of above for Method Constant Pool Entries
    private short getMethodRef(String ownerClass, String methodName, String methodDesc) {
        short callNameIndex = findOrPut(new ConstantPoolEntry.Utf8(methodName));
        short callNameDescriptorIndex = findOrPut(new ConstantPoolEntry.Utf8(methodDesc));
        short callNameAndTypeIndex = findOrPut(new ConstantPoolEntry.NameAndType(callNameIndex, callNameDescriptorIndex));
        short classIndex = getClassConstant(ownerClass);

        return findOrPut(new ConstantPoolEntry.Method(classIndex, callNameAndTypeIndex));
    }

    // Variant of above for Field Constant Pool Entries
    private short getFieldRef(String ownerClass, String fieldName, String fieldDesc) {
        short callNameIndex = findOrPut(new ConstantPoolEntry.Utf8(fieldName));
        short callNameDescriptorIndex = findOrPut(new ConstantPoolEntry.Utf8(fieldDesc));
        short callNameAndTypeIndex = findOrPut(new ConstantPoolEntry.NameAndType(callNameIndex, callNameDescriptorIndex));
        short classIndex = getClassConstant(ownerClass);

        return findOrPut(new ConstantPoolEntry.Field(classIndex, callNameAndTypeIndex));
    }

    // Helpers to write larger types or other ByteArrayOutputStreams
    // to a ByteArrayOutputStream, without casting and catching everywhere

    private void putInt(ByteArrayOutputStream bytes, int intToPut) {
        bytes.write(intToPut >>> 24 & 0xFF);
        bytes.write(intToPut >>> 16 & 0xFF);
        bytes.write(intToPut >>> 8 & 0xFF);
        bytes.write(intToPut & 0xFF);
    }

    private void putShort(ByteArrayOutputStream bytes, int shortToPut) {
        bytes.write(shortToPut >>> 8 & 0xFF);
        bytes.write(shortToPut & 0xFF);
    }

    private static void append(ByteArrayOutputStream baos, ByteArrayOutputStream toAppend) {
        append(baos, toAppend.toByteArray());
    }

    private static void append(ByteArrayOutputStream baos, byte[] toAppend) {
        try {
            baos.write(toAppend);
        } catch (IOException e) {
            throw new IllegalStateException(e); // Impossible
        }
    }

    private byte[] genHex() {
        /* -- Setup -- */
        short thisClassIndex = getClassConstant(className);
        short superClassIndex = getClassConstant("java/lang/Object");

        /* -- Generate code for all methods -- */
        // This is done up here so the constant pool is populated

        List<byte[]> funHex = new ArrayList<>();
        boolean foundMain = false;
        int mainFunArgCount = 0;

        for (Fun fun : funs) {
            if ("main".equals(fun.name)) {
                foundMain = true;
                mainFunArgCount = fun.formals.size();
            }
            funHex.add(genMethod(fun));
        }

        if (!foundMain) {
            throw new CompileException("No main fun found");
        }

        // Generate code for class initializer, empty constructor, and entry point
        ByteArrayOutputStream clinit = genCtor(true);
        ByteArrayOutputStream init = genCtor(false);
        ByteArrayOutputStream mainMethod = genMainMethod(mainFunArgCount);

        // Generate code for comparison helpers. This is because there is no "icmp" instruction in JVM. See report.
        ByteArrayOutputStream isEq = genConditionalHelper(EQ);
        ByteArrayOutputStream isLt = genConditionalHelper(LT);
        ByteArrayOutputStream isGt = genConditionalHelper(GT);

        /* -- Actual file -- */

        // Magic header for class files
        putInt(bytes, 0xCAFEBABE);

        // Compiling for JVM 6u0 (version 50.0)
        putShort(bytes, 0);    // minor 0
        putShort(bytes, 0x32); // major 50

        // Constant pool size
        putShort(bytes, constantPoolEntries.size() + 1);

        // Constant pool
        for (ConstantPoolEntry e : constantPoolEntries) {
            append(bytes, e.getBytes());
        }

        // Access flags
        short flags = 0;
        flags |= 0x0001; // PUBLIC
        flags |= 0x0010; // FINAL
        flags |= 0x1000; // SYNTHETIC
        putShort(bytes, flags);

        // what is this class
        putShort(bytes, thisClassIndex);

        // who is the superclass
        putShort(bytes, superClassIndex);

        // interface count and no interfaces
        putShort(bytes, 0);

        // field count
        putShort(bytes, globalVars.size());

        // fields
        for (String var : globalVars) {
            append(bytes, genGlobalVar(var));
        }

        // method count
        putShort(bytes, funHex.size() + 6);

        // methods
        append(bytes, clinit);
        append(bytes, init);
        append(bytes, mainMethod);

        append(bytes, isGt);
        append(bytes, isLt);
        append(bytes, isEq);

        for (byte[] arr : funHex) {
            append(bytes, arr);
        }

        // class attribute count and no class attributes
        putShort(bytes, 0);
        return bytes.toByteArray();
    }

    private ByteArrayOutputStream genGlobalVar(String name) {
        ByteArrayOutputStream ret = new ByteArrayOutputStream(8);
        short flags = 0;
        flags |= 0x0002; // PRIVATE
        flags |= 0x0008; // STATIC
        flags |= 0x1000; // SYNTHETIC
        putShort(ret, flags);

        // Name
        putShort(ret, findOrPut(new ConstantPoolEntry.Utf8(name)));

        // Descriptor
        putShort(ret, findOrPut(new ConstantPoolEntry.Utf8("I")));

        // No attributes
        putShort(ret, 0);

        return ret;
    }

    /**
     * Generates the true main method.
     * Simply pushes a number of 0's matching the formal argument count of the fun main function (now mangled to $main), then invokes it.
     */
    private ByteArrayOutputStream genMainMethod(int mainFunArgCount) {
        ByteArrayOutputStream ret = new ByteArrayOutputStream(38);
        
        short flags = 0;
        flags |= 0x0001; // PUBLIC
        flags |= 0x0008; // STATIC
        flags |= 0x0080; // VARARGS
        flags |= 0x1000; // SYNTHETIC

        putShort(ret, flags);

        short nameIndex = findOrPut(new ConstantPoolEntry.Utf8("main"));
        putShort(ret, nameIndex);

        short descriptorIndex = findOrPut(new ConstantPoolEntry.Utf8("([Ljava/lang/String;)V"));
        putShort(ret, descriptorIndex);

        // Attribute count
        putShort(ret, 0x1);

        // code attribute
        putShort(ret, findOrPut(new ConstantPoolEntry.Utf8("Code")));

        // code attribute attribute length (12 + codeLength below this)
        int codeAttribLength = 12 + (mainFunArgCount + 5);
        putInt(ret, codeAttribLength);

        // max stack
        putShort(ret, mainFunArgCount); // pushing 0's

        // max locals
        putShort(ret, 1); // args parameter

        // code length
        int codeLength = mainFunArgCount + 5; // Push args (n) + invokestatic (3) + pop (1) + return (1)
        putInt(ret, codeLength);

        for (int i = 0; i < mainFunArgCount; i++) {
            // iconst_0
            ret.write(0x03);
        }

        // invokestatic
        ret.write(0xB8);
        putShort(ret, getMethodRef(className, "$main", "(" + String.join("", Collections.nCopies(mainFunArgCount, "I")) + ")I"));

        // pop $main's return value
        ret.write(0x57);

        // Return
        ret.write(0xB1);

        // exception table length
        putShort(ret, 0);

        // attrib table length
        putShort(ret, 0);

        return ret;
    }

    /**
     * Generates a conditional helper.
     * This provides a bridge between Java's compareTo-style semantics and C's zero-nonzero semantics.
     * The conditional helper takes two ints and calls Integer.compareUnsiged, which returns -1, 0, or 1
     * The helper than translates that into a 1 (true) or 0 (false) value based on the ExpressionType being generated.
     */
    private ByteArrayOutputStream genConditionalHelper(ExpressionType type) {
        if (type != LT && type != GT && type != EQ)
            throw new CompileException("Invalid expression type passed into genCondtionalHelper");

        ByteArrayOutputStream ret = new ByteArrayOutputStream();

        short flags = 0;
        flags |= 0x0002; // PRIVATE
        flags |= 0x0008; // STATIC
        flags |= 0x1000; // SYNTHETIC

        putShort(ret, flags);

        String mName = "$";
        switch (type) {
            case LT: mName += "isLt"; break;
            case GT: mName += "isGt"; break;
            case EQ: mName += "isEq"; break;
        }

        putShort(ret, findOrPut(new ConstantPoolEntry.Utf8(mName)));

        short descriptorIndex = findOrPut(new ConstantPoolEntry.Utf8("(II)I"));
        putShort(ret, descriptorIndex);

        // Attribute count
        putShort(ret, 1);

        // code attribute
        putShort(ret, findOrPut(new ConstantPoolEntry.Utf8("Code")));

        // code attribute attribute length (12 + codeLength)
        int codeAttribLength = 12 + 14;
        putInt(ret, codeAttribLength);

        // max stack
        putShort(ret, 2);

        // max locals
        putShort(ret, 2);

        // code length
        putInt(ret, 14);

        // code

        // iload_0
        ret.write(0x1A);

        // iload_1
        ret.write(0x1B);

        // invokestatic
        ret.write(0xB8);
        putShort(ret, getMethodRef("java/lang/Integer", "compareUnsigned", "(II)I"));

        // ifCC branch forward from this byte to iconst_1
        int insn = 0;
        switch (type) {
            case LT: insn = 0x9B; break; // iflt
            case GT: insn = 0x9D; break; // ifgt
            case EQ: insn = 0x99; break; // ifeq
        }
        ret.write(insn);
        putShort(ret, 7);

        // iconst_0
        ret.write(0x03);

        // goto over the iconst_1
        ret.write(0xA7);
        putShort(ret, 4);

        // iconst_1
        ret.write(0x04);

        // ireturn
        ret.write(0xAC);

        // exception table length
        putShort(ret, 0);

        // attrib table length
        putShort(ret, 0);

        return ret;
    }

    /**
     * Generates the constructor or static initializer.
     * Nothing particularly interesting happens here.
     */
    private ByteArrayOutputStream genCtor(boolean isStatic) {
        ByteArrayOutputStream ret = new ByteArrayOutputStream(27);

        short flags = 0;
        flags |= 0x0001; // PUBLIC
        if (isStatic) flags |= 0x0008; // STATIC
        flags |= 0x1000; // SYNTHETIC

        putShort(ret, flags);

        // Name
        putShort(ret, findOrPut(new ConstantPoolEntry.Utf8(isStatic ? "<clinit>" : "<init>")));

        // Descriptor
        putShort(ret, findOrPut(new ConstantPoolEntry.Utf8("()V")));

        // Attribute count
        putShort(ret, 1);

        // code attribute
        putShort(ret, findOrPut(new ConstantPoolEntry.Utf8("Code")));

        // code attribute attribute length (13 bytes below this one)
        putInt(ret, isStatic ? 13 : 17); // need to call super if not static

        // max stack
        putShort(ret, isStatic ? 0 : 1); // need one to load "this" before calling super

        // max locals
        putShort(ret, isStatic ? 0 : 1); // need one local to store "this"

        // code length
        putInt(ret, isStatic ? 1 : 5);

        if (!isStatic) {
            // aload_0
            ret.write(0x2A);

            // invokespecial
            ret.write(0xB7);
            putShort(ret, getMethodRef("java/lang/Object", "<init>", "()V"));
        }

        // return
        ret.write(0xB1);

        // exception table length
        putShort(ret, 0);

        // attrib table length
        putShort(ret, 0);

        return ret;
    }

    private Fun findFun(String name) {
        for (Fun f : funs) {
            if (name.equals(f.name)){
                return f;
            }
        }

        return null;
    }

    private byte[] genMethod(Fun func) {
        ByteArrayOutputStream ret = new ByteArrayOutputStream();

        short flags = 0;
        flags |= 0x0002; // PRIVATE
        flags |= 0x0008; // STATIC
        flags |= 0x1000; // SYNTHETIC

        putShort(ret, flags);

        // If this is the main function, mangle the name by prepending "$"
        boolean mangle = func.name.equals("main");
        putShort(ret, findOrPut(new ConstantPoolEntry.Utf8((mangle ? "$" : "") + func.name)));

        short descriptorIndex = findOrPut(new ConstantPoolEntry.Utf8("(" + String.join("", Collections.nCopies(func.formals.size(), "I")) + ")I"));
        putShort(ret, descriptorIndex);

        // Attribute count
        putShort(ret, 1);

        // code attribute
        putShort(ret, findOrPut(new ConstantPoolEntry.Utf8("Code")));

        ByteArrayOutputStream code = fun(func);

        // code attribute attribute length (12 + codeLength)
        int codeAttribLength = 12 + code.size();
        putInt(ret, codeAttribLength);

        // max stack
        short maxStack = (short) maxStackTracker.stream().mapToInt(Integer::intValue).max().orElse(0);
        putShort(ret, maxStack);

        // max locals
        putShort(ret, func.formals.size());

        // code length
        putInt(ret, code.size());

        // code
        append(ret, code);

        // exception table length
        putShort(ret, 0);

        // attrib table length
        putShort(ret, 0);

        return ret.toByteArray();
    }

    /**
     * This is a semi-cheaty way to try to find out how deep the stack goes during this method call,
     * without implementing the advanced data and control flow analysis that javac has.
     * Whenever bytecode is generated that pushes or pops from the stack these methods are called and the number stored.
     * The maximum value is found and used (above).
     * It's primitive and should prefer to overestimate, and thus should be safe.
     */

    private final List<Integer> maxStackTracker = new ArrayList<>();
    private int curStack = 0;

    private void pushed() {
        pushed(1);
    }

    private void popped() {
        popped(1);
    }

    private void pushed(int count) {
        curStack += count;
        maxStackTracker.add(curStack);
    }

    private void popped(int count) {
        curStack -= count;
        if (curStack < 0)
            throw new CompileException("Pop tracker went below 0");
        maxStackTracker.add(curStack);
    }

    private ByteArrayOutputStream fun(Fun fun) {
        maxStackTracker.clear();
        curStack = 0;

        ByteArrayOutputStream ret = statement(fun, fun.body);


        // Implicit return 0 at end
        // iconst_0
        ret.write(0x03);
        pushed();
        // ireturn
        ret.write(0xAC);
        popped();

        return ret;
    }

    private ByteArrayOutputStream statement(Fun fun, Statement s) {
        switch (s.kind) {
            case BLOCK: {
                ByteArrayOutputStream ret = new ByteArrayOutputStream();
                for (Statement sub : ((Statement.Block) s).block)
                    append(ret, statement(fun, sub));
                return ret;
            }
            case ASSIGNMENT: {
                Statement.Assign assign = ((Statement.Assign) s);
                boolean global = !fun.formals.contains(assign.assignName);
                ByteArrayOutputStream ret = new ByteArrayOutputStream();

                append(ret, expression(fun, assign.assignValue));

                if (global) {
                    // putstatic
                    globalVars.add(assign.assignName);
                    ret.write(0xB3);
                    putShort(ret, getFieldRef(className, assign.assignName, "I"));
                } else {
                    int storeIndex = fun.formals.indexOf(assign.assignName);
                    switch (storeIndex) {
                        case 0: ret.write(0x3B); break; // istore_0
                        case 1: ret.write(0x3C); break; // istore_1
                        case 2: ret.write(0x3D); break; // istore_2
                        case 3: ret.write(0x3E); break; // istore_3
                        default: {
                            // istore
                            ret.write(0x36);
                            ret.write((byte) storeIndex);
                            break;
                        }
                    }
                }

                popped();
                return ret;
            }
            case PRINT: {
                ByteArrayOutputStream ret = new ByteArrayOutputStream();

                // getstatic System.out
                ret.write(0xB2);
                putShort(ret, getFieldRef("java/lang/System", "out", "Ljava/io/PrintStream;"));
                pushed();

                append(ret, expression(fun, ((Statement.Print) s).printValue));

                // invokestatic
                ret.write(0xB8);
                putShort(ret, getMethodRef("java/lang/Integer", "toUnsignedString", "(I)Ljava/lang/String;"));
                popped();
                pushed();

                // invokevirtual
                ret.write(0xB6);
                putShort(ret, getMethodRef("java/io/PrintStream", "println", "(Ljava/lang/String;)V"));
                popped(2);

                return ret;
            }
            case IF: {
                Statement.If ifStatement = ((Statement.If) s);

                ByteArrayOutputStream ifCondition = expression(fun, ifStatement.ifCondition);
                ByteArrayOutputStream trueBranch = statement(fun, ifStatement.ifThen);
                ByteArrayOutputStream elseBranch = ifStatement.ifElse == null ? new ByteArrayOutputStream(0) : statement(fun, ifStatement.ifElse);

                ByteArrayOutputStream ret = new ByteArrayOutputStream();

                append(ret, ifCondition);

                // ifne, jump over elseBranch
                ret.write(0x9A);
                putShort(ret, (2 + elseBranch.size() + 3) + 1); // These two bytes + else branch code + goto at end of else branch

                append(ret, elseBranch);

                // goto over true branch
                // We generate these even if there is no else branch specified in fun code (elseBranch has size 0)
                // A minor optimization could be applied here to remove a useless goto.
                ret.write(0xA7);
                putShort(ret, (2 + trueBranch.size()) + 1); // These two bytes + true branch code

                append(ret, trueBranch);

                return ret;
            }
            case WHILE: {
                Statement.While whileStatement = ((Statement.While) s);
                ByteArrayOutputStream whileCondition = expression(fun, whileStatement.whileCondition);
                ByteArrayOutputStream whileBody = statement(fun, whileStatement.whileBody);
                ByteArrayOutputStream ret = new ByteArrayOutputStream();

                append(ret, whileCondition);

                // ifeq jump over body
                ret.write(0x99);
                putShort(ret, (2 + whileBody.size() + 3) + 1); // These two bytes + body code + loopback

                append(ret, whileBody);

                // goto back to condition check
                ret.write(0xA7);
                putShort(ret, -(whileBody.size() + 3 + whileCondition.size())); // Back over body + two bytes from if check + conditional

                return ret;
            }
            case RETURN: {
                Statement.Return retStatement = ((Statement.Return) s);
                ByteArrayOutputStream ret = expression(fun, retStatement.returnValue);
                // ireturn
                ret.write(0xAC);
                popped();
                return ret;
            }
            default: throw new CompileException("Unknown statement type: " + s);
        }
    }

    /**
     * Postcondition of executing the bytecode produced by this method:
     *     Expression result value (int) is on top of stack after completion
     *     No extra elements on stack
     */

    private ByteArrayOutputStream expression(Fun fun, Expression e) {
        switch (e.kind) {
            case VAR: {
                Expression.Var varExp = ((Expression.Var) e);
                boolean global = !fun.formals.contains(varExp.varName);
                ByteArrayOutputStream ret = new ByteArrayOutputStream();

                if (global) {
                    globalVars.add(varExp.varName);
                    // getstatic
                    ret.write(0xB2);
                    putShort(ret, getFieldRef(className, varExp.varName, "I"));
                } else {
                    int loadIndex = fun.formals.indexOf(varExp.varName);
                    switch (loadIndex) {
                        case 0: ret.write(0x1A); break; // iload_0
                        case 1: ret.write(0x1B); break; // iload_1
                        case 2: ret.write(0x1C); break; // iload_2
                        case 3: ret.write(0x1D); break; // iload_3
                        default: {
                            // iload
                            ret.write(0x15);
                            ret.write((byte) loadIndex);

                            break;
                        }
                    }
                }

                pushed();
                return ret;
            }
            case VAL: {
                Expression.Val valExp = ((Expression.Val) e);
                ByteArrayOutputStream ret = new ByteArrayOutputStream();

                switch (valExp.value_unsigned) {
                    case 0: ret.write(0x3); break; // iconst_0
                    case 1: ret.write(0x4); break; // iconst_1
                    case 2: ret.write(0x5); break; // iconst_2
                    case 3: ret.write(0x6); break; // iconst_3
                    case 4: ret.write(0x7); break; // iconst_4
                    case 5: ret.write(0x8); break; // iconst_5
                    default: {
                        // put into constant pool
                        short index = findOrPut(new ConstantPoolEntry.Int(valExp.value_unsigned));

                        // ldc
                        ret.write(0x12);
                        ret.write((byte) index);

                        break;
                    }
                }

                pushed();
                return ret;
            }

            case PLUS:
            case MUL:
            case EQ:
            case NE:
            case LT:
            case GT: {
                Expression.BinaryExpr expr = ((Expression.BinaryExpr) e);
                ByteArrayOutputStream left = expression(fun, expr.left);
                ByteArrayOutputStream right = expression(fun, expr.right);

                ByteArrayOutputStream ret = new ByteArrayOutputStream();
                append(ret, left);
                append(ret, right);

                if (e.kind == ExpressionType.PLUS) {
                    ret.write(0x60); // iadd
                    popped(2);
                    pushed();
                } else if (e.kind == ExpressionType.MUL) {
                    ret.write(0x68); // imul
                    popped(2);
                    pushed();
                } else {
                    // invokestatic our condtional helpers
                    ret.write(0xB8);
                    String callName;

                    switch (e.kind) {
                        case LT: callName = "$isLt"; break;
                        case GT: callName = "$isGt"; break;
                        case EQ:
                        case NE: callName = "$isEq"; break;
                        default: callName = "PANIC_IF_THIS_APPEARS"; break;
                    }

                    putShort(ret, getMethodRef(className, callName, "(II)I"));
                    popped(2);
                    pushed();

                    // If NE, invert result of call to $isEq()
                    if (e.kind == NE) {
                        // iconst_1
                        ret.write(0x04);
                        pushed();
                        // ixor by 1 (flips 1-0 and vice versa)
                        ret.write(0x82);
                        popped(2);
                        pushed();
                    }
                }

                return ret;
            }
            case CALL: {
                Expression.Call call = ((Expression.Call) e);
                Fun receiver = findFun(call.callName);
                if (receiver == null)
                    throw new CompileException("Calling something nonexistent");
                if (call.callActuals.size() < receiver.formals.size())
                    throw new CompileException("Not enough arguments");

                ByteArrayOutputStream ret = new ByteArrayOutputStream();

                // actuals guaranteed to be >= formals vua above. Extra actuals ignored at compile time.
                for (int i = 0; i < receiver.formals.size(); i++) {
                    append(ret, expression(fun, call.callActuals.get(i)));
                }

                // invokestatic
                ret.write(0xB8);
                boolean mangle = call.callName.equals("main");
                putShort(ret, getMethodRef(className, (mangle ? "$" : "") + call.callName, "(" + String.join("", Collections.nCopies(receiver.formals.size(), "I")) + ")I"));

                popped(receiver.formals.size());
                pushed();

                return ret;
            }
            default: throw new CompileException("Unknown expression type: " + e);
        }
    }

}
