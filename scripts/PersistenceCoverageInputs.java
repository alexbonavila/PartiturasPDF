import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import org.jacoco.core.analysis.*;
import org.jacoco.core.data.*;
import org.jacoco.core.instr.Instrumenter;
import org.objectweb.asm.Opcodes;

/** JaCoCo's own readers, class IDs and probe model; stdout is machine-readable JSON only. */
public class PersistenceCoverageInputs {
    static final String ROOT = "com/abdev/partituraspdf/data/";
    static boolean scoped(String name) {
        return (System.getProperty("local.coverage.sources") != null
                ? name.startsWith("com/abdev/partituraspdf/")
                : (name.startsWith(ROOT + "metadata/") || name.startsWith(ROOT + "preferences/")))
            && !name.substring(name.lastIndexOf('/') + 1).equals("DocumentDao_Impl")
            && !name.substring(name.lastIndexOf('/') + 1).startsWith("DocumentDao_Impl$")
            && !name.substring(name.lastIndexOf('/') + 1).equals("MetadataDatabase_Impl")
            && !name.substring(name.lastIndexOf('/') + 1).startsWith("MetadataDatabase_Impl$");
    }
    static Map<String, Object> inventory(Path directory) throws Exception {
        return inventory(directory, true);
    }
    static Map<String, Object> inventory(Path directory, boolean required) throws Exception {
        if (!Files.isDirectory(directory)) throw new IOException("Missing compiled debug classes");
        Map<String, Object> result = new TreeMap<>();
        try (var paths = Files.walk(directory)) {
            for (Path path : paths.filter(p -> p.toString().endsWith(".class")).sorted().toList()) {
                byte[] bytes = Files.readAllBytes(path);
                org.objectweb.asm.ClassReader raw = new org.objectweb.asm.ClassReader(bytes);
                if (!scoped(raw.getClassName())) continue;
                String[] source = {null};
                raw.accept(new org.objectweb.asm.ClassVisitor(Opcodes.ASM9) {
                    public void visitSource(String name, String debug) { source[0] = name; }
                }, org.objectweb.asm.ClassReader.SKIP_CODE);
                CoverageBuilder builder = new CoverageBuilder();
                new Analyzer(new ExecutionDataStore(), builder).analyzeClass(bytes, path.toString());
                IClassCoverage c = builder.getClasses().stream().findFirst().orElse(null);
                long identity = c == null ? org.jacoco.core.internal.data.CRC64.classId(bytes) : c.getId();
                int[] probes = {-1};
                new Instrumenter((id, name, count, visitor) -> {
                    probes[0] = count;
                    visitor.visitInsn(Opcodes.ACONST_NULL);
                    return 1;
                }).instrument(bytes, path.toString());
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("id", Long.toUnsignedString(identity, 16));
                entry.put("sha256", HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
                entry.put("path", directory.relativize(path).toString());
                entry.put("source", source[0]);
                if (System.getProperty("local.coverage.sources") != null) {
                    Path sourceRoot = Path.of(System.getProperty("local.coverage.sources"));
                    String pkg = raw.getClassName().substring(0, raw.getClassName().lastIndexOf('/') + 1);
                    entry.put("production", source[0] != null && Files.isRegularFile(sourceRoot.resolve(pkg + source[0])));
                }
                entry.put("reported", c != null); // Only JaCoCo's supplied class filter determines this.
                entry.put("probes", probes[0]);
                entry.put("branches", c == null ? 0 : c.getBranchCounter().getTotalCount());
                List<Integer> lines = new ArrayList<>();
                if (c != null) for (int line = c.getFirstLine(); line <= c.getLastLine(); line++)
                    if (c.getLine(line).getInstructionCounter().getTotalCount() > 0) lines.add(line);
                entry.put("lines", lines);
                result.put(raw.getClassName(), entry);
            }
        }
        if (required && result.isEmpty()) throw new IOException("No persistence classes found");
        return result;
    }
    static Map<String, Object> testInventory(Path classesDirectory) throws Exception {
        Path intermediates = classesDirectory.getParent().getParent().getParent().getParent();
        Map<String, Object> tests = new TreeMap<>();
        for (String variant : List.of("debugUnitTest", "debugAndroidTest")) {
            for (String suffix : List.of("built_in_kotlinc/" + variant + "/compile" +
                    Character.toUpperCase(variant.charAt(0)) + variant.substring(1) + "Kotlin/classes",
                    "javac/" + variant + "/compile" + Character.toUpperCase(variant.charAt(0)) +
                    variant.substring(1) + "JavaWithJavac/classes")) {
                Path path = intermediates.resolve(suffix);
                if (!Files.isDirectory(path)) continue;
                for (var entry : inventory(path, false).entrySet()) {
                    Object old = tests.putIfAbsent(entry.getKey(), entry.getValue());
                    if (old != null && !old.equals(entry.getValue())) throw new IOException("Incompatible duplicate compiled test class");
                }
            }
        }
        return tests;
    }
    static List<Object> readInputs(Map<String, Object> classes, String[] files) throws Exception {
        return readInputs(classes, Map.of(), files);
    }
    static List<Object> readInputs(Map<String, Object> classes, Map<String, Object> tests, String[] files) throws Exception {
        Map<String, ExecutionData> identities = new HashMap<>();
        Map<Long, String> idNames = new HashMap<>();
        List<Object> results = new ArrayList<>();
        for (String file : files) {
            List<Object> sessions = new ArrayList<>();
            Map<String, Object> records = new TreeMap<>();
            Map<String, Object> testRecords = new TreeMap<>();
            Map<String, boolean[]> mergedProbes = new HashMap<>();
            try (InputStream stream = new BufferedInputStream(Files.newInputStream(Path.of(file)))) {
                stream.mark(5);
                if (stream.read() != ExecutionDataWriter.BLOCK_HEADER) throw new IOException("Missing JaCoCo header: " + file);
                stream.reset();
                ExecutionDataReader reader = new ExecutionDataReader(stream);
                reader.setSessionInfoVisitor(s -> sessions.add(Map.of("id", s.getId(), "start", s.getStartTimeStamp(), "end", s.getDumpTimeStamp())));
                reader.setExecutionDataVisitor(e -> {
                    ExecutionData old = identities.putIfAbsent(e.getName(), e);
                    if (old != null && (old.getId() != e.getId() || old.getProbes().length != e.getProbes().length))
                        throw new IllegalStateException("Incompatible duplicate class record: " + e.getName());
                    String oldName = idNames.putIfAbsent(e.getId(), e.getName());
                    if (oldName != null && !oldName.equals(e.getName())) throw new IllegalStateException("One class ID has multiple names");
                    if (!scoped(e.getName())) return; // Dependencies/test classes are not report denominators.
                    boolean isTest = !classes.containsKey(e.getName()) && tests.containsKey(e.getName());
                    @SuppressWarnings("unchecked") Map<String, Object> c = (Map<String, Object>)
                        (isTest ? tests : classes).get(e.getName());
                    if (c == null) throw new IllegalStateException("Unexpected persistence execution class: " + e.getName());
                    if (!c.get("id").equals(Long.toUnsignedString(e.getId(), 16))) throw new IllegalStateException("Class ID mismatch: " + e.getName());
                    if (((Number)c.get("probes")).intValue() != e.getProbes().length) throw new IllegalStateException("Probe count mismatch: " + e.getName());
                    boolean[] merged = mergedProbes.computeIfAbsent(e.getName(), n -> new boolean[e.getProbes().length]);
                    long covered = 0;
                    for (int i = 0; i < merged.length; i++) {
                        merged[i] |= e.getProbes()[i];
                        if (merged[i]) covered++;
                    }
                    (isTest ? testRecords : records).put(e.getName(), Map.of("id", c.get("id"), "probes", e.getProbes().length, "coveredProbes", covered));
                });
                while (reader.read()) { }
            }
            if (sessions.isEmpty() || records.isEmpty()) throw new IOException("Missing sessions or persistence records: " + file);
            results.add(Map.of("path", file, "sessions", sessions, "records", records, "testRecords", testRecords));
        }
        return results;
    }
    static String json(Object value) {
        if (value == null) return "null";
        if (value instanceof Number || value instanceof Boolean) return value.toString();
        if (value instanceof Map<?, ?> map) {
            List<String> items = new ArrayList<>();
            for (var e : map.entrySet()) items.add(json(e.getKey().toString()) + ":" + json(e.getValue()));
            return "{" + String.join(",", items) + "}";
        }
        if (value instanceof Iterable<?> list) {
            List<String> items = new ArrayList<>(); for (Object e : list) items.add(json(e));
            return "[" + String.join(",", items) + "]";
        }
        String s = value.toString(); StringBuilder b = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            if (c == '"' || c == '\\') b.append('\\').append(c);
            else if (c < 32) b.append(String.format("\\u%04x", (int)c));
            else b.append(c);
        }
        return b.append('"').toString();
    }
    public static void main(String[] args) throws Exception {
        if (args.length < 2) throw new IllegalArgumentException("inventory|validate classes-dir [exec files...]");
        Map<String, Object> classes = inventory(Path.of(args[1]));
        if (args[0].equals("inventory")) System.out.println(json(classes));
        else if (args[0].equals("test-inventory")) System.out.println(json(testInventory(Path.of(args[1]))));
        else if (args[0].equals("validate") && args.length > 2)
            System.out.println(json(Map.of("classes", classes, "inputs", readInputs(classes, testInventory(Path.of(args[1])), Arrays.copyOfRange(args, 2, args.length)))));
        else throw new IllegalArgumentException("Unknown mode or missing execution inputs");
    }
}
