import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import student.TestCase;

/**
 * This class was designed to test the Radix class by generating a random
 * ascii and binary file, sorting both and then checking each one with a file
 * checker.
 *
 * @author Peter Reilly (preilly) Brenden Jeffryes (jbrenden)
 * @version Final
 */
public class RadixProjTest extends TestCase {
    private CheckFile fileChecker;

    private PrintWriter stats;
    
    /**
     * This method sets up the tests that follow.
     * @throws IOException 
     */
    public void setUp() throws IOException {
        fileChecker = new CheckFile();
        stats = new PrintWriter(new BufferedWriter(
            new FileWriter("Stats.txt", true)));
    }


    /**
     * Fail a sort
     *
     * @throws Exception
     *             either a IOException or FileNotFoundException
     */
    public void testFailSort() throws Exception {
        FileGenerator it = new FileGenerator();
        it.generateFile("input.txt", 1, "b");
        assertFalse(fileChecker.checkFile("input.txt"));
        System.out.println("Done testFailSort");
    }


    /**
     * Tests the sort by generating a random file. Compared with fileChecker
     * 
     * @throws Exception
     */
    public void testSort() throws Exception {
        FileGenerator it = new FileGenerator();
        it.generateFile("input.txt", 1, "b");
        RandomAccessFile raf = new RandomAccessFile("input.txt", "rw");
        Radix rad = new Radix(raf, stats);
        assertTrue(fileChecker.checkFile("input.txt"));
        System.out.println("Done testSort");
    }
    
    
    
    /**
     * Tests if the sort is stable
     * 
     * @throws Exception
     */
    public void testSortStable() throws Exception {
        FileGenerator it = new FileGenerator();
        it.generateFile("input.txt", 1, "c");
        RandomAccessFile raf = new RandomAccessFile("input.txt", "rw");
        Radix rad = new Radix(raf, stats);
        assertTrue(fileChecker.checkFileStrong("input.txt"));
        System.out.println("Done testSort");
    }
    
}
