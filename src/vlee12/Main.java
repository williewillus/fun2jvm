package vlee12;

import vlee12.compiler.CompileException;
import vlee12.compiler.Compiler;
import vlee12.parser.Fun;
import vlee12.parser.Parser;

import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class Main {

    public static void main(String[] args) {
        if (args.length == 0)
            System.err.println("Need input file!");
        else {
            try {
                Path p = Paths.get(args[0]);
                List<Fun> funs = Parser.parse(Files.newBufferedReader(p));
                funs.forEach(System.out::println);
                byte[] byteCode = Compiler.compile(funs, p.getFileName().toString());
                try (DataOutputStream out = new DataOutputStream(new FileOutputStream(p.getFileName().toString() + ".class"))) {
                    out.write(byteCode);
                }
            } catch (IOException | CompileException ex) {
                ex.printStackTrace();
            }
        }
    }
}
