package vlee12.compiler;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

abstract class ConstantPoolEntry {
    final EntryType type;

    private ConstantPoolEntry(EntryType type) {
        this.type = type;
    }

    abstract byte[] getBytes();

    static class Utf8 extends ConstantPoolEntry {

        private final byte[] ret;

        Utf8(java.lang.String data) {
            super(EntryType.UTF8);

            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            // Writes using JVM's "modified UTF8" format
            try {
                out.writeUTF(data);
            } catch (IOException e) {
                throw new CompileException("Couldn't create UTF8 entry");
            }

            ret = new byte[bytes.size() + 1];
            ret[0] = type.id;
            ret[1] = (byte) ((bytes.size() - 2) >>> 8);
            ret[2] = (byte) (bytes.size() - 2);

            // DataOutputStream adds the length of the string as two bytes at the start, skip those.
            System.arraycopy(bytes.toByteArray(), 2, ret, 3, bytes.size() - 2);
        }

        @Override
        byte[] getBytes() {
            return ret;
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(ret);
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Utf8 && Arrays.equals(ret, ((Utf8) o).ret);
        }
    }

    static class Int extends ConstantPoolEntry {

        private final byte[] ret;

        Int(int val) {
            super(EntryType.INTEGER);
            ret = new byte[5];
            ret[0] = type.id;
            ret[1] = (byte) (val >>> 24);
            ret[2] = (byte) (val >>> 16);
            ret[3] = (byte) (val >>> 8);
            ret[4] = (byte) (val);
        }

        @Override
        byte[] getBytes() {
            return ret;
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(ret);
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Int && Arrays.equals(ret, ((Int) o).ret);
        }
    }

    static class Class extends ConstantPoolEntry {

        private final byte[] ret;

        Class(short index) {
            super(EntryType.CLASS);
            ret = new byte[3];
            ret[0] = type.id;
            ret[1] = (byte) (index >>> 8);
            ret[2] = (byte) (index);
        }

        @Override
        byte[] getBytes() {
            return ret;
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(ret);
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Class && Arrays.equals(ret, ((Class) o).ret);
        }
    }

    static class Field extends ConstantPoolEntry {

        private final byte[] ret;

        Field(short classIndex, short nameTypeIndex) {
            super(EntryType.FIELD);
            ret = new byte[5];
            ret[0] = type.id;
            ret[1] = (byte) (classIndex >>> 8);
            ret[2] = (byte) (classIndex);
            ret[3] = (byte) (nameTypeIndex >>> 8);
            ret[4] = (byte) (nameTypeIndex);
        }

        @Override
        byte[] getBytes() {
            return ret;
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(ret);
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Field && Arrays.equals(ret, ((Field) o).ret);
        }
    }

    static class Method extends ConstantPoolEntry {

        private final byte[] ret;

        Method(short classIndex, short nameTypeIndex) {
            super(EntryType.METHOD);
            ret = new byte[5];
            ret[0] = type.id;
            ret[1] = (byte) (classIndex >>> 8);
            ret[2] = (byte) (classIndex);
            ret[3] = (byte) (nameTypeIndex >>> 8);
            ret[4] = (byte) (nameTypeIndex);
        }

        @Override
        byte[] getBytes() {
            return ret;
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(ret);
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Method && Arrays.equals(ret, ((Method) o).ret);
        }
    }

    static class NameAndType extends ConstantPoolEntry {

        private final byte[] ret;

        NameAndType(short nameIndex, short descriptorIndex) {
            super(EntryType.NAME_AND_TYPE);
            ret = new byte[5];
            ret[0] = type.id;
            ret[1] = (byte) (nameIndex >>> 8);
            ret[2] = (byte) (nameIndex);
            ret[3] = (byte) (descriptorIndex >>> 8);
            ret[4] = (byte) (descriptorIndex);
        }

        @Override
        byte[] getBytes() {
            return ret;
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(ret);
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof NameAndType && Arrays.equals(ret, ((NameAndType) o).ret);
        }
    }

    public enum EntryType {
        UTF8(1),
        INTEGER(3),
        CLASS(7),
        FIELD(9),
        METHOD(10),
        NAME_AND_TYPE(12);

        public final byte id;

        EntryType(int id) {
            this.id = ((byte) id);
        }
    }
}
