package vlee12.test;

import junit.framework.TestSuite;
import org.junit.runner.RunWith;
import org.junit.runners.AllTests;

import java.io.File;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@RunWith(AllTests.class)
public class MainSuite {

    public static TestSuite suite() {
        TestSuite ret = new TestSuite("Root suite");
        File testDir = new File(".");

        // Gather all .fun and .ok
        File[] funs = testDir.listFiles((dir, name) -> name.endsWith(".fun"));
        File[] oks = testDir.listFiles((dir, name) -> name.endsWith(".ok"));
        Set<String> toTest = new HashSet<>();

        if (funs != null && oks != null) {
            List<File> funsList = Arrays.asList(funs);
            List<String> oksNames = Arrays.stream(oks).map(File::getName).collect(Collectors.toList());

            for (File f : funsList) {
                String testName = f.getName().substring(0, f.getName().lastIndexOf(".fun"));
                if (oksNames.contains(testName + ".ok")) {
                    // For all funs, if .fun has a .ok, add to test list
                    toTest.add(testName);
                } else {
                    System.out.printf("%s has no .ok file, skipping ...%n", testName);
                }
            }
        }

        toTest.forEach(s -> ret.addTest(new FunTest(s)));
        return ret;
    }

}
