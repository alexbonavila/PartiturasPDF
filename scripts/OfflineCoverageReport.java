import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.jacoco.core.analysis.*;
import org.jacoco.core.tools.ExecFileLoader;
import org.jacoco.report.*;
import org.jacoco.report.html.HTMLFormatter;
import org.jacoco.report.xml.XMLFormatter;

/** Host-only report generation from an explicitly verified retained class list. No build/test APIs. */
public class OfflineCoverageReport {
    public static void main(String[] args) throws Exception {
        if (args.length < 5) throw new IllegalArgumentException("xml html source-root class-list exec...");
        var loader = new ExecFileLoader();
        for (int i = 4; i < args.length; i++) loader.load(new File(args[i]));
        var builder = new CoverageBuilder();
        var analyzer = new Analyzer(loader.getExecutionDataStore(), builder);
        for (String name : Files.readAllLines(Path.of(args[3])))
            analyzer.analyzeClass(Files.readAllBytes(Path.of(name)), name);
        var sources = new MultiSourceFileLocator(4);
        for (String variant : List.of("main", "debug"))
            sources.add(new DirectorySourceFileLocator(Path.of(args[2], variant, "java").toFile(), "UTF-8", 4));
        try (var xml = Files.newOutputStream(Path.of(args[0]))) {
            var visitor = new MultiReportVisitor(List.of(
                new XMLFormatter().createVisitor(xml),
                new HTMLFormatter().createVisitor(new FileMultiReportOutput(new File(args[1])))));
            visitor.visitInfo(loader.getSessionInfoStore().getInfos(), loader.getExecutionDataStore().getContents());
            visitor.visitBundle(builder.getBundle("POST_HOC_REANALYSIS"), sources);
            visitor.visitEnd();
        }
    }
}
