package vlee12.test;

import junit.framework.TestCase;
import org.junit.Assert;
import vlee12.Main;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

public class FunTest extends TestCase {

    public FunTest(String testName) {
        super(testName);
        setName(testName);
    }

    @Override
    protected void setUp() {
        // Generate class file for the fun
        Main.main(getName() + ".fun");
    }

    @Override
    public void runTest() throws IOException, InterruptedException {
        Process p = Runtime.getRuntime().exec("java -noverify " + getName() + " 2>&1");
        p.waitFor();

        List<String> procOutput;
        List<String> expected;

        try (BufferedReader pout = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
            procOutput = pout.lines()
                    .filter(s -> !s.trim().isEmpty()) // Ignore whitespace lines
                    .collect(Collectors.toList());
        }

        expected = Files
                .lines(Paths.get(getName() + ".ok"))
                .filter(s -> !s.trim().isEmpty()) // Ignore whitespace lines
                .collect(Collectors.toList());

        Assert.assertEquals(expected, procOutput);
    }

    @Override
    protected void tearDown() throws IOException {
        // Delete generated class file
        Paths.get(getName() + ".class").toFile().delete();
    }
}
