import com.sun.source.tree.BlockTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.DoWhileLoopTree;
import com.sun.source.tree.EnhancedForLoopTree;
import com.sun.source.tree.ForLoopTree;
import com.sun.source.tree.IfTree;
import com.sun.source.tree.StatementTree;
import com.sun.source.tree.WhileLoopTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.SourcePositions;
import com.sun.source.util.TreeScanner;
import com.sun.source.util.Trees;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.ToolProvider;

/** Adds braces using Java syntax trees; google-java-format handles the final layout. */
public final class ReadableJavaFormat {
    private record Insertion(int offset, String text) {}

    public static void main(String[] args) throws Exception {
        boolean check = args.length > 0 && args[0].equals("--check");
        List<Path> paths = new ArrayList<>();
        for (int i = check ? 1 : 0; i < args.length; i++) {
            paths.add(Path.of(args[i]));
        }
        if (paths.isEmpty()) {
            throw new IllegalArgumentException("Provide Java source paths");
        }
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        Map<Path, String> replacements = new LinkedHashMap<>();
        try (var files = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8)) {
            JavacTask task =
                    (JavacTask)
                            compiler.getTask(
                                    null,
                                    files,
                                    diagnostics,
                                    List.of("-proc:none", "-source", "17"),
                                    null,
                                    files.getJavaFileObjectsFromPaths(paths));
            Iterable<? extends CompilationUnitTree> units = task.parse();
            SourcePositions positions = Trees.instance(task).getSourcePositions();
            for (CompilationUnitTree unit : units) {
                List<Insertion> insertions = new ArrayList<>();
                new TreeScanner<Void, Void>() {
                    private void wrap(StatementTree statement) {
                        if (statement == null || statement instanceof BlockTree) {
                            return;
                        }
                        long start = positions.getStartPosition(unit, statement);
                        long end = positions.getEndPosition(unit, statement);
                        if (start < 0 || end < start) {
                            throw new IllegalStateException("Missing source position");
                        }
                        insertions.add(new Insertion(Math.toIntExact(start), "{\n"));
                        insertions.add(new Insertion(Math.toIntExact(end), "\n}"));
                    }

                    @Override
                    public Void visitIf(IfTree tree, Void unused) {
                        wrap(tree.getThenStatement());
                        if (!(tree.getElseStatement() instanceof IfTree)) {
                            wrap(tree.getElseStatement());
                        }
                        return super.visitIf(tree, unused);
                    }

                    @Override
                    public Void visitForLoop(ForLoopTree tree, Void unused) {
                        wrap(tree.getStatement());
                        return super.visitForLoop(tree, unused);
                    }

                    @Override
                    public Void visitEnhancedForLoop(EnhancedForLoopTree tree, Void unused) {
                        wrap(tree.getStatement());
                        return super.visitEnhancedForLoop(tree, unused);
                    }

                    @Override
                    public Void visitWhileLoop(WhileLoopTree tree, Void unused) {
                        wrap(tree.getStatement());
                        return super.visitWhileLoop(tree, unused);
                    }

                    @Override
                    public Void visitDoWhileLoop(DoWhileLoopTree tree, Void unused) {
                        wrap(tree.getStatement());
                        return super.visitDoWhileLoop(tree, unused);
                    }
                }.scan(unit, null);
                if (!insertions.isEmpty()) {
                    Path path = Path.of(unit.getSourceFile().toUri());
                    StringBuilder content = new StringBuilder(Files.readString(path));
                    insertions.sort(Comparator.comparingInt(Insertion::offset).reversed());
                    for (Insertion insertion : insertions) {
                        content.insert(insertion.offset(), insertion.text());
                    }
                    replacements.put(path, content.toString());
                }
            }
            if (diagnostics.getDiagnostics().stream()
                    .anyMatch(diagnostic -> diagnostic.getKind() == Diagnostic.Kind.ERROR)) {
                diagnostics.getDiagnostics().forEach(System.err::println);
                throw new IllegalStateException("Cannot format invalid Java source");
            }
        }
        for (var entry : replacements.entrySet()) {
            if (check) {
                System.err.println("Missing control-flow braces: " + entry.getKey());
            } else {
                Files.writeString(entry.getKey(), entry.getValue(), StandardCharsets.UTF_8);
            }
        }
        if (check && !replacements.isEmpty()) {
            System.exit(1);
        }
    }
}
