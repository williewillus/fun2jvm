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

    public static void main(String... args) {
        if (args.length == 0)
            System.err.println("Need input file!");
        else if (validateArgs(args)) {
            try {
                Path p = Paths.get(args[0]);
                List<Fun> funs = Parser.parse(Files.newBufferedReader(p));
                funs.forEach(System.out::println);

                String name = p.getFileName().toString();
                String realName = name.substring(0, name.indexOf(".fun"));

                byte[] byteCode = Compiler.compile(funs, realName);
                try (DataOutputStream out = new DataOutputStream(new FileOutputStream(realName + ".class"))) {
                    out.write(byteCode);
                }
            } catch (IOException | CompileException ex) {
                ex.printStackTrace();
            }
        }
    }

    private static boolean validateArgs(String[] args) {
        if (!args[0].contains(".fun")) {
            System.err.println("Must be a .fun file");
            return false;
        }

        return true;
    }
}
