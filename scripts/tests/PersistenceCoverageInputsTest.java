import java.nio.file.*;
import java.util.*;
import org.jacoco.core.data.*;

/** Synthetic files exercise only the reader/identity validator, never application coverage. */
public class PersistenceCoverageInputsTest {
    interface Checked { void run() throws Exception; }
    static int tests;
    static void rejected(Checked action) throws Exception {
        try { action.run(); } catch (Exception expected) { tests++; return; }
        throw new AssertionError("Invalid input was accepted");
    }
    @SuppressWarnings("unchecked")
    static ExecutionData record(Map<String, Object> inventory, String name, long delta) {
        Map<String, Object> c = (Map<String, Object>) inventory.get(name);
        boolean[] probes = new boolean[((Number)c.get("probes")).intValue()];
        if (probes.length > 0) probes[0] = true;
        return new ExecutionData(Long.parseUnsignedLong((String)c.get("id"), 16) + delta, name, probes);
    }
    static void write(Path path, boolean session, ExecutionData... records) throws Exception {
        try (var output = Files.newOutputStream(path)) {
            ExecutionDataWriter writer = new ExecutionDataWriter(output);
            if (session) writer.visitSessionInfo(new SessionInfo("validator-test-only", 100, 200));
            for (ExecutionData e : records) writer.visitClassExecution(e);
        }
    }
    public static void main(String[] args) throws Exception {
        Map<String, Object> inventory = PersistenceCoverageInputs.inventory(Path.of(args[0]));
        String registry = PersistenceCoverageInputs.ROOT + "metadata/RoomDocumentRegistry";
        String metadata = PersistenceCoverageInputs.ROOT + "metadata/RoomDocumentMetadataRepository";
        String preferences = PersistenceCoverageInputs.ROOT + "preferences/DataStorePreferencesRepository";
        Path root = Files.createTempDirectory("jacoco-validator-tests-");
        try {
            Path jvm = root.resolve("jvm.exec"), android = root.resolve("android.ec");
            write(jvm, true, record(inventory, registry, 0));
            write(android, true, record(inventory, registry, 0), record(inventory, metadata, 0), record(inventory, preferences, 0));
            if (PersistenceCoverageInputs.readInputs(inventory, new String[]{jvm.toString(), android.toString()}).size() != 2)
                throw new AssertionError("Valid inputs rejected");
            tests++;
            Map<String, Object> testClasses = PersistenceCoverageInputs.testInventory(Path.of(args[0]));
            String testName = PersistenceCoverageInputs.ROOT + "metadata/MetadataMappingTest";
            write(android, true, record(inventory, registry, 0), record(testClasses, testName, 0));
            PersistenceCoverageInputs.readInputs(inventory, testClasses, new String[]{android.toString()}); tests++;
            rejected(() -> PersistenceCoverageInputs.readInputs(inventory, new String[]{android.toString()}));
            write(android, true, record(inventory, registry, 0), record(testClasses, testName, 1));
            rejected(() -> PersistenceCoverageInputs.readInputs(inventory, testClasses, new String[]{android.toString()}));
            String filtered = PersistenceCoverageInputs.ROOT + "metadata/MetadataMapping$WhenMappings";
            write(android, true, record(inventory, registry, 0), record(inventory, filtered, 0));
            PersistenceCoverageInputs.readInputs(inventory, new String[]{android.toString()}); tests++;
            rejected(() -> PersistenceCoverageInputs.readInputs(inventory, new String[]{root.resolve("absent.ec").toString()}));
            Files.write(android, new byte[]{1, 2, 3});
            rejected(() -> PersistenceCoverageInputs.readInputs(inventory, new String[]{android.toString()}));
            write(android, true, record(inventory, preferences, 0));
            byte[] valid = Files.readAllBytes(android);
            Files.write(android, Arrays.copyOf(valid, valid.length - 1));
            rejected(() -> PersistenceCoverageInputs.readInputs(inventory, new String[]{android.toString()}));
            write(android, true, record(inventory, preferences, 1));
            rejected(() -> PersistenceCoverageInputs.readInputs(inventory, new String[]{android.toString()}));
            write(android, true, record(inventory, registry, 0), record(inventory, registry, 1));
            rejected(() -> PersistenceCoverageInputs.readInputs(inventory, new String[]{android.toString()}));
            write(android, true, new ExecutionData(123, PersistenceCoverageInputs.ROOT + "metadata/Unexpected", new boolean[]{true}));
            rejected(() -> PersistenceCoverageInputs.readInputs(inventory, new String[]{android.toString()}));
            write(android, false, record(inventory, preferences, 0));
            rejected(() -> PersistenceCoverageInputs.readInputs(inventory, new String[]{android.toString()}));
            write(android, true);
            rejected(() -> PersistenceCoverageInputs.readInputs(inventory, new String[]{android.toString()}));
            ExecutionData correct = record(inventory, preferences, 0);
            write(android, true, new ExecutionData(correct.getId(), correct.getName(), new boolean[correct.getProbes().length + 1]));
            rejected(() -> PersistenceCoverageInputs.readInputs(inventory, new String[]{android.toString()}));
            write(android, true, record(inventory, registry, 1));
            rejected(() -> PersistenceCoverageInputs.readInputs(inventory, new String[]{jvm.toString(), android.toString()}));
            write(android, true, record(inventory, registry, 0), new ExecutionData(
                record(inventory, registry, 0).getId(), metadata, record(inventory, metadata, 0).getProbes()));
            rejected(() -> PersistenceCoverageInputs.readInputs(inventory, new String[]{android.toString()}));
            boolean[] different = new boolean[correct.getProbes().length];
            if (different.length < 2) throw new AssertionError("Fixture requires multiple real probes");
            different[1] = true;
            write(android, true, correct, new ExecutionData(correct.getId(), correct.getName(), different));
            var compatible = PersistenceCoverageInputs.readInputs(inventory, new String[]{android.toString()});
            @SuppressWarnings("unchecked") var input = (Map<String, Object>) compatible.get(0);
            @SuppressWarnings("unchecked") var records = (Map<String, Object>) input.get("records");
            @SuppressWarnings("unchecked") var merged = (Map<String, Object>) records.get(preferences);
            if (((Number)merged.get("coveredProbes")).intValue() != 2) throw new AssertionError("Compatible dumps must union probes");
            tests++;
            write(jvm, true, correct);
            write(android, true, new ExecutionData(correct.getId(), correct.getName(), different));
            PersistenceCoverageInputs.readInputs(inventory, new String[]{jvm.toString(), android.toString()});
            var mergedFiles = new org.jacoco.core.tools.ExecFileLoader();
            mergedFiles.load(jvm.toFile()); mergedFiles.load(android.toFile());
            int coveredAcrossFiles = 0;
            for (boolean probe : mergedFiles.getExecutionDataStore().get(correct.getId()).getProbes())
                if (probe) coveredAcrossFiles++;
            if (coveredAcrossFiles != 2) throw new AssertionError("Real JaCoCo loader must union complementary runtime probes");
            tests++;
            if (args.length > 1) {
                System.setProperty("local.coverage.sources", args[1]);
                try {
                    var all = PersistenceCoverageInputs.inventory(Path.of(args[0]));
                    String shared = "com/abdev/partituraspdf/core/document/DocumentId";
                    String debug = "com/abdev/partituraspdf/storage/saf/SafGrantAcceptanceActivity";
                    @SuppressWarnings("unchecked") var handwritten = (Map<String, Object>) all.get(shared);
                    @SuppressWarnings("unchecked") var debugOnly = (Map<String, Object>) all.get(debug);
                    if (handwritten == null || !Boolean.TRUE.equals(handwritten.get("production")) ||
                        debugOnly == null || !Boolean.FALSE.equals(debugOnly.get("production")))
                        throw new AssertionError("Handwritten main/debug inventories must remain distinct");
                    tests++;
                    write(jvm, true, record(all, shared, 0));
                    PersistenceCoverageInputs.readInputs(all, new String[]{jvm.toString()}); tests++;
                    write(android, true, record(all, shared, 1));
                    rejected(() -> PersistenceCoverageInputs.readInputs(all, new String[]{android.toString()}));
                    ExecutionData sharedRecord = record(all, shared, 0);
                    write(android, true, new ExecutionData(sharedRecord.getId(), shared,
                        new boolean[sharedRecord.getProbes().length + 1]));
                    rejected(() -> PersistenceCoverageInputs.readInputs(all, new String[]{android.toString()}));
                } finally {
                    System.clearProperty("local.coverage.sources");
                }
            }
            System.out.println("PASS " + tests + " JaCoCo parser/identity infrastructure cases; synthetic data not retained");
        } finally {
            try (var paths = Files.walk(root)) {
                for (Path p : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(p);
            }
        }
    }
}
