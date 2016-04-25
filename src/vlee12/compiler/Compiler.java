package vlee12.compiler;

import vlee12.parser.Expression;
import vlee12.parser.ExpressionType;
import vlee12.parser.Fun;
import vlee12.parser.Statement;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class Compiler {
    public static byte[] compile(List<Fun> funs, String className) {
        try {
            return new Compiler(funs, className).genHex();
        } catch (IOException neverHappens) {
            return new byte[0];
        }
    }

    private final List<Fun> funs;
    private final Set<String> globalVars = new HashSet<>();
    private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    private final List<ConstantPoolEntry> constantPoolEntries = new ArrayList<>();
    private final String className;

    private Compiler(List<Fun> funs, String name) {
        this.funs = funs;
        this.className = "fun2jvm/gen/" + name;
    }

    private short findOrPut(ConstantPoolEntry entry) {
        int index = constantPoolEntries.indexOf(entry);
        if (index == -1) {
            constantPoolEntries.add(entry);
            return ((short) constantPoolEntries.size());
        } else {
            return ((short) (index + 1));
        }
    }

    private short getClassConstant(String name) {
        short entryIndex = findOrPut(new ConstantPoolEntry.Utf8(name));
        constantPoolEntries.add(new ConstantPoolEntry.Class(entryIndex));
        return ((short) constantPoolEntries.size());
    }

    private short getMethodRef(String ownerClass, String methodName, String methodDesc) {
        short callNameIndex = findOrPut(new ConstantPoolEntry.Utf8(methodName));
        short callNameDescriptorIndex = findOrPut(new ConstantPoolEntry.Utf8(methodDesc));
        short callNameAndTypeIndex = findOrPut(new ConstantPoolEntry.NameAndType(callNameIndex, callNameDescriptorIndex));
        short classIndex = getClassConstant(ownerClass);

        return findOrPut(new ConstantPoolEntry.Method(classIndex, callNameAndTypeIndex));
    }

    private short getFieldRef(String ownerClass, String fieldName, String fieldDesc) {
        short callNameIndex = findOrPut(new ConstantPoolEntry.Utf8(fieldName));
        short callNameDescriptorIndex = findOrPut(new ConstantPoolEntry.Utf8(fieldDesc));
        short callNameAndTypeIndex = findOrPut(new ConstantPoolEntry.NameAndType(callNameIndex, callNameDescriptorIndex));
        short classIndex = getClassConstant(ownerClass);

        return findOrPut(new ConstantPoolEntry.Field(classIndex, callNameAndTypeIndex));
    }

    private void putShort(ByteArrayOutputStream bytes, int shortToPut) {
        if (shortToPut != ((short) shortToPut)) {
            System.err.println("Short will be truncated!");
        }
        bytes.write(shortToPut >>> 8 & 0xFF);
        bytes.write(shortToPut & 0xFF);
    }

    private byte[] genHex() throws IOException {
        // -- Setup -- //
        short thisClassIndex = getClassConstant(className);
        short superClassIndex = getClassConstant("java/lang/Object");

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
        
        byte[] clinit = genCtor(true);
        byte[] init = genCtor(false);
        byte[] mainMethod = genMainMethod(mainFunArgCount);
        
        // -- Actual file -- //

        // Magic header for class files
        putShort(bytes, 0xCAFE);
        putShort(bytes, 0xBABE);

        // Compiling for JVM 6u0 (version 50.0)
        bytes.write(0x00); // minor 0
        bytes.write(0x32); // major 50

        // Constant pool size
        putShort(bytes, constantPoolEntries.size() + 1);

        // Constant pool
        for (ConstantPoolEntry e : constantPoolEntries) {
            bytes.write(e.getBytes());
        }

        // Access flags
        short flags = 0;
        flags |= 0x0001; // PUBLIC
        flags |= 0x0010; // FINAL
        flags |= 0x1000; // SYNTHETIC
        putShort(bytes, flags);

        // this class
        putShort(bytes, thisClassIndex);

        // superclass
        putShort(bytes, superClassIndex);

        // interface count and no interfaces
        putShort(bytes, 0);

        // field count
        putShort(bytes, globalVars.size());

        // fields
        for (String var : globalVars) {
            bytes.write(genGlobalVar(var));
        }

        // method count
        putShort(bytes, funs.size());

        // methods
        bytes.write(clinit);
        bytes.write(init);
        bytes.write(mainMethod);

        for (byte[] arr : funHex) {
            bytes.write(arr);
        }

        // class attribute count and no class attributes
        putShort(bytes, 0);

        return bytes.toByteArray();
    }

    private byte[] genGlobalVar(String name) {
        byte[] ret = new byte[8];
        short flags = 0;
        flags |= 0x0002; // PRIVATE
        flags |= 0x0008; // STATIC
        flags |= 0x1000; // SYNTHETIC
        ret[0] = (byte) (flags >>> 8 & 0xFF);
        ret[1] = (byte) (flags & 0xFF);

        short nameIndex = findOrPut(new ConstantPoolEntry.Utf8(name));
        ret[2] = (byte) (nameIndex >>> 8 & 0xFF);
        ret[3] = (byte) (nameIndex & 0xFF);

        short descriptorIndex = findOrPut(new ConstantPoolEntry.Utf8("J"));
        ret[4] = (byte) (descriptorIndex >>> 8 & 0xFF);
        ret[5] = (byte) (descriptorIndex & 0xFF);

        // No attributes
        ret[6] = 0;
        ret[7] = 0;

        return ret;
    }

    private byte[] genMainMethod(int mainFunArgCount) {
        ByteArrayOutputStream ret = new ByteArrayOutputStream();
        
        short flags = 0;
        flags |= 0x0001; // PUBLIC
        flags |= 0x0008; // STATIC
        flags |= 0x1000; // SYNTHETIC

        ret.write(flags >>> 8 & 0xFF);
        ret.write(flags & 0xFF);

        short nameIndex = findOrPut(new ConstantPoolEntry.Utf8("main"));
        ret.write(nameIndex >>> 8 & 0xFF);
        ret.write(nameIndex & 0xFF);

        short descriptorIndex = findOrPut(new ConstantPoolEntry.Utf8("([Ljava/lang/String;)V"));
        ret.write(descriptorIndex >>> 8 & 0xFF);
        ret.write(descriptorIndex & 0xFF);

        // Attribute count
        ret.write(0);
        ret.write(1);

        // code attribute
        putShort(ret, findOrPut(new ConstantPoolEntry.Utf8("Code")));

        // code attribute attribute length (12 + codeLength below this)
        int codeAttribLength = 12 + (mainFunArgCount + 4);
        ret.write(codeAttribLength >>> 24 & 0xFF);
        ret.write(codeAttribLength >>> 16 & 0xFF);
        ret.write(codeAttribLength >>> 8 & 0xFF);
        ret.write(codeAttribLength & 0xFF);

        // max stack
        putShort(ret, mainFunArgCount * 2);

        // max locals
        putShort(ret, 0);

        // code length
        int codeLength = mainFunArgCount + 4; // Push args (n) + invokestatic (3) + return (1)
        ret.write(codeLength >>> 24 & 0xFF);
        ret.write(codeLength >>> 16 & 0xFF);
        ret.write(codeLength >>> 8 & 0xFF);
        ret.write(codeLength & 0xFF);

        for (int i = 0; i < mainFunArgCount; i++) {
            ret.write(0x09); // lconst_0
        }

        // invokestatic
        ret.write(0xB8);
        putShort(ret, getMethodRef(className, "$main", "(" + String.join("", Collections.nCopies(mainFunArgCount, "J")) + ")J"));

        // Return
        ret.write(0xB1);

        // exception table length
        ret.write(0);
        ret.write(0);

        // attrib table length
        ret.write(0);
        ret.write(0);

        return ret.toByteArray();
    }

    private byte[] genCtor(boolean isStatic) {
        byte[] ret = new byte[27];
        short flags = 0;
        flags |= 0x0001; // PUBLIC
        if (isStatic)
            flags |= 0x0008; // STATIC
        flags |= 0x1000; // SYNTHETIC

        ret[0] = (byte) (flags >>> 8);
        ret[1] = (byte) (flags);

        short nameIndex = findOrPut(new ConstantPoolEntry.Utf8(isStatic ? "<clinit>" : "<init>"));
        ret[2] = (byte) (nameIndex >>> 8);
        ret[3] = (byte) nameIndex;

        short descriptorIndex = findOrPut(new ConstantPoolEntry.Utf8("()V"));
        ret[4] = (byte) (descriptorIndex >>> 8);
        ret[5] = (byte) descriptorIndex;

        // Attribute count
        ret[6] = 0;
        ret[7] = 1;

        // code attribute
        short codeIndex = findOrPut(new ConstantPoolEntry.Utf8("Code"));
        ret[8] = (byte) (codeIndex >>> 8);
        ret[9] = (byte) codeIndex;

        // code attribute attribute length (13 bytes below this one)
        ret[10] = ret[11] = ret[12] = 0;
        ret[13] = 13;

        // max stack
        ret[14] = ret[15] = 0;

        // max locals
        ret[16] = ret[17] = 0;

        // code length
        ret[18] = ret[19] = ret[20] = 0;
        ret[21] = 1;

        // Return
        ret[22] = (byte) 0xB1;

        // exception table length
        ret[23] = ret[24] = 0;

        // attrib table length
        ret[25] = ret[26] = 0;

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

    private byte[] genMethod(Fun func) throws IOException {
        ByteArrayOutputStream ret = new ByteArrayOutputStream();

        short flags = 0;
        flags |= 0x0001; // PUBLIC
        flags |= 0x0008; // STATIC
        flags |= 0x1000; // SYNTHETIC

        ret.write(flags >>> 8 & 0xFF);
        ret.write(flags & 0xFF);

        boolean mangle = func.name.equals("main");
        putShort(ret, findOrPut(new ConstantPoolEntry.Utf8((mangle ? "$" : "") + func.name)));

        short descriptorIndex = findOrPut(new ConstantPoolEntry.Utf8("(" + String.join("", Collections.nCopies(func.formals.size(), "J")) + ")J"));
        putShort(ret, descriptorIndex);

        // Attribute count
        putShort(ret, 1);

        // code attribute
        putShort(ret, findOrPut(new ConstantPoolEntry.Utf8("Code")));

        byte[] code = fun(func).toByteArray();

        // code attribute attribute length (12 + codeLength)
        int codeAttribLength = 12 + code.length;
        ret.write(codeAttribLength >>> 24 & 0xFF);
        ret.write(codeAttribLength >>> 16 & 0xFF);
        ret.write(codeAttribLength >>> 8 & 0xFF);
        ret.write(codeAttribLength & 0xFF);

        // max stack
        short maxStack = 0; // todo ??? Longs take double space on the stack
        putShort(ret, maxStack);

        // max locals
        putShort(ret, func.formals.size());

        // code length
        ret.write(code.length >>> 24 & 0xFF);
        ret.write(code.length >>> 16 & 0xFF);
        ret.write(code.length >>> 8 & 0xFF);
        ret.write(code.length & 0xFF);

        // code
        ret.write(code);

        // exception table length
        putShort(ret, 0);

        // attrib table length
        putShort(ret, 0);

        return ret.toByteArray();
    }

    private ByteArrayOutputStream fun(Fun fun) {
        ByteArrayOutputStream ret = statement(fun, fun.body);
        ret.write(0x09); // lconst_0
        ret.write(0xAD); // lreturn
        return ret;
    }

    private ByteArrayOutputStream statement(Fun fun, Statement s) {
        switch (s.kind) {
            case BLOCK: {
                ByteArrayOutputStream ret = new ByteArrayOutputStream();
                for (Statement sub : ((Statement.Block) s).block) {
                    append(ret, statement(fun, sub));
                }
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
                    getFieldRef(className, assign.assignName, "J");
                } else {
                    // lstore
                    ret.write(0x37);
                    putShort(ret, 2 * fun.formals.indexOf(assign.assignName) + 1); // longs take double space in lv array
                }
                return ret;
            }
            case PRINT: {
                ByteArrayOutputStream ret = expression(fun, ((Statement.Print) s).printValue);

                // getstatic System.out
                ret.write(0xB2);
                putShort(ret, getFieldRef("java/lang/System", "out", "Ljava/io/PrintStream;"));

                // invokevirtual
                ret.write(0xB6);
                putShort(ret, getMethodRef("java/io/PrintStream", "println", "(J)V"));

                return ret;
            }
            case IF: {
                Statement.If ifStatement = ((Statement.If) s);
                ByteArrayOutputStream ifCondition = expression(fun, ifStatement.ifCondition);
                ByteArrayOutputStream trueBranch = statement(fun, ifStatement.ifThen);
                ByteArrayOutputStream elseBranch = statement(fun, ifStatement.ifElse);

                ByteArrayOutputStream ret = new ByteArrayOutputStream();
                append(ret, ifCondition);
                ret.write(); // ifne, jump to true branch
                append(ret, elseBranch);
                ret.write(); // jump over true branch
                append(ret, trueBranch);

                return ret;
            }
            case WHILE: {
                Statement.While whileStatement = ((Statement.While) s);
                ByteArrayOutputStream whileCondition = expression(fun, whileStatement.whileCondition);
                ByteArrayOutputStream whileBody = statement(fun, whileStatement.whileBody);

                ByteArrayOutputStream ret = new ByteArrayOutputStream();
                append(ret, whileCondition);
                ret.write(); // ifeq, jump over body and loopback
                append(ret, whileBody);
                ret.write(); // Loopback to condition check

                return ret;
            }
            case RETURN: {
                Statement.Return retStatement = ((Statement.Return) s);
                ByteArrayOutputStream ret = expression(fun, retStatement.returnValue);
                ret.write(0xAD); // lreturn
                return ret;
            }
            default: throw new CompileException("Unknown statement type: " + s);
        }
    }

    // Postcondition of bytes: Expression result value (long) is on top of stack after completion
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
                    getFieldRef(className, varExp.varName, "J");
                } else {
                    // lload
                    ret.write(0x16);
                    putShort(ret, 2 * fun.formals.indexOf(varExp.varName) + 1); // longs take double space in lv array
                }
                return ret;
            }
            case VAL: {
                Expression.Val valExp = ((Expression.Val) e);
                ByteArrayOutputStream ret = new ByteArrayOutputStream();

                if (valExp.value_unsigned == 0)
                    ret.write(0x09); // lconst_0
                else if (valExp.value_unsigned == 1)
                    ret.write(0x0A); // lconst_1
                else {
                    // put into constant pool
                    short index = findOrPut(new ConstantPoolEntry.Long(valExp.value_unsigned));

                    // ldc
                    ret.write(0x14);
                    ret.write(index >>> 8 & 0xFF);
                    ret.write(index & 0xFF);
                }

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

                if (e.kind == ExpressionType.PLUS)
                    ret.write(0x61); // ladd
                else if (e.kind == ExpressionType.MUL)
                    ret.write(0x69); // lmul
                else {
                    // Long.compareUnsigned(long, long): int

                    // invokestatic
                    ret.write(0xB8);
                    putShort(ret, getMethodRef("java/lang/Long", "compareUnsigned", "(JJ)I"));
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
                // actuals guaranteed >= formals. Extra actuals ignored at compile time.
                for (int i = 0; i < receiver.formals.size(); i++) {
                    append(ret, expression(fun, call.callActuals.get(i)));
                }

                // invokestatic
                ret.write(0xB8);
                putShort(ret, getMethodRef(className, call.callName, "(" + String.join("", Collections.nCopies(call.callActuals.size(), "J")) + ")J"));

                return ret;
            }
            default: throw new CompileException("Unknown expression type: " + e);
        }
    }

    private static void append(ByteArrayOutputStream baos, ByteArrayOutputStream toAppend) {
        try {
            baos.write(toAppend.toByteArray());
        } catch (IOException e) {
            throw new RuntimeException(e); // Impossible
        }
    }

}
