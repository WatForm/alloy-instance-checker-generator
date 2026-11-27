/*
    Requires Java 17+ and libs/org.alloytools.alloy.dist-6.2.0.jar.
    Download the Alloy 6.2.0 distribution JAR from Maven Central if needed.

    Compile:
    javac -cp libs/org.alloytools.alloy.dist-6.2.0.jar InstanceEvaluator.java

    Run:
    java -cp .:libs/org.alloytools.alloy.dist-6.2.0.jar InstanceEvaluator model.als instance.xml
    Optional: -cmd=N (or --command=N), --no-overflow

    Default: evaluate a synthetic run {} plus signature facts.
    -cmd=N selects a zero-based explicit model command instead.
    Exit codes: 0 satisfied, 1 false, 2 input/evaluation error.
    See README.md for semantic gaps: true does not certify instance validity.
*/

import edu.mit.csail.sdg.alloy4.A4Reporter;
import edu.mit.csail.sdg.alloy4.XMLNode;
import edu.mit.csail.sdg.parser.CompUtil;
import edu.mit.csail.sdg.translator.A4SolutionReader;
import edu.mit.csail.sdg.translator.TranslateAlloyToKodkod;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import kodkod.ast.Formula;
import kodkod.engine.Evaluator;
import kodkod.engine.config.Options;

/** Evaluates available Alloy formulas, rather than solving to validate an entire instance.
 * See README.md for declaration and command-bound constraints omitted by this pathway.
 */
public final class InstanceEvaluator {
    private InstanceEvaluator() {}

    private static final String USAGE =
        "Usage: InstanceEvaluator MODEL XML [-cmd=N|--command=N] [--no-overflow]\n"
        + "Evaluate native Alloy formulas against XML (not full instance validation).\n"
        + "Default: synthetic run {}. -cmd selects a zero-based model command.\n"
        + "Checks evaluate the negated assertion.\n"
        + "Exit codes: 0 satisfied, 1 false, 2 input/evaluation error.";

    public static void main(String[] args) {
        System.exit(run(args));
    }

    static int run(String... args) {
        if (args.length == 1 && (args[0].equals("--help") || args[0].equals("-h"))) {
            System.out.println(USAGE);
            return 0;
        }
        try {
            if (args.length < 2) throw new IllegalArgumentException(USAGE);
            Integer command = null;
            boolean noOverflow = false;
            for (int i = 2; i < args.length; i++) {
                String arg = args[i];
                if (arg.equals("--no-overflow")) {
                    if (noOverflow) throw new IllegalArgumentException("Duplicate --no-overflow");
                    noOverflow = true;
                } else if (arg.startsWith("-cmd=") || arg.startsWith("--command=")) {
                    if (command != null) throw new IllegalArgumentException("Duplicate command selection");
                    command = Integer.parseInt(arg.substring(arg.indexOf('=') + 1));
                    if (command < 0) throw new IllegalArgumentException("Command index must be nonnegative");
                } else {
                    throw new IllegalArgumentException("Unknown option: " + arg + "\n" + USAGE);
                }
            }
            Path model = Path.of(args[0]).toRealPath();
            Path xml = Path.of(args[1]).toRealPath();
            if (!Files.isRegularFile(model) || !Files.isRegularFile(xml))
                throw new IllegalArgumentException("MODEL and XML must be regular files");

            // Append an explicit default run without changing model command indices.
            var sources = new HashMap<String, String>();
            sources.put(model.toString(), Files.readString(model) + "\nrun {}\n");
            var module = CompUtil.parseEverything_fromFile(A4Reporter.NOP, sources, model.toString());
            int count = module.getAllCommands().size() - 1;
            if (command != null && command >= count)
                throw new IllegalArgumentException("Command index " + command
                    + " out of range: model has " + count + " explicit commands");
            var solution = A4SolutionReader.read(module.getAllReachableSigs(), new XMLNode(xml.toFile()));
            var options = new Options();
            options.setBitwidth(solution.getBitwidth());
            options.setNoOverflow(noOverflow);
            var evaluator = new Evaluator(solution.debugExtractKInstance(), options);
            // A resolved command includes module facts and the run body/negated assertion.
            var constraints = module.getAllCommands().get(command == null ? count : command).formula;
            for (var sig : module.getAllReachableUserDefinedSigs()) {
                for (var fact : sig.getFacts()) {
                    var signatureFact = fact.forAll(sig.decl);
                    if (solution.isTemporal()) signatureFact = signatureFact.always();
                    constraints = constraints.and(signatureFact);
                }
            }
            Formula formula = (Formula) TranslateAlloyToKodkod.alloy2kodkod(solution, constraints);
            boolean satisfied = evaluator.evaluate(formula);
            System.out.println((satisfied ? "PASS" : "FAIL")
                + ": native formula evaluation = " + satisfied);
            return satisfied ? 0 : 1;
        } catch (Exception error) {
            System.err.println("ERROR: native formula evaluation: " + error.getMessage());
            return 2;
        }
    }
}
