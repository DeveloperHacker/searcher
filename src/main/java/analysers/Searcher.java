package analysers;

import analysers.analysable.AstMethod;
import analysers.analysable.DaikonMethod;
import analysers.analysable.MethodDescription;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseException;
import com.github.javaparser.ast.CompilationUnit;
import javassist.NotFoundException;
import org.apache.maven.shared.utils.io.DirectoryScanner;
import org.javatuples.Pair;
import org.objectweb.asm.ClassReader;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class Searcher {

    private final Map<MethodDescription, Set<MethodDescription>> indexedMethods = new HashMap<>();
    private final Map<MethodDescription, Pair<AstMethod, DaikonMethod>> methods = new HashMap<>();

    private Searcher() {

    }

    public Pair<AstMethod, DaikonMethod> getMethod(MethodDescription description) {
        return this.methods.get(description);
    }

    public Set<Pair<AstMethod, DaikonMethod>> getMethods() {
        return new HashSet<>(this.methods.values());
    }

    private Set<MethodDescription> usages(MethodDescription description) {
        return this.indexedMethods.get(description);
    }

    public Pair<Pair<AstMethod, DaikonMethod>, Set<Pair<AstMethod, DaikonMethod>>> associate(MethodDescription description) throws NotFoundException {
        if (!this.methods.containsKey(description)) return null;
        final Pair<AstMethod, DaikonMethod> pair = this.methods.get(description);
        final Set<Pair<AstMethod, DaikonMethod>> usages = this.usages(description).stream().map(this::getMethod).collect(Collectors.toSet());
        return new Pair<>(pair, usages);
    }

    private static Set<String> loadJava(String passToFolder) {
        final DirectoryScanner scanner = new DirectoryScanner();
        scanner.setIncludes("**/*.java");
        scanner.setBasedir(passToFolder);
        scanner.setCaseSensitive(false);
        scanner.scan();
        final String[] codes = scanner.getIncludedFiles();
        return Arrays.stream(codes).map(code -> passToFolder + "/" + code).collect(Collectors.toSet());
    }

    private static Map<MethodDescription, Set<MethodDescription>> indexByteCodes(Set<String> byteCodes, AsmClassAnalyser analyser) throws IOException {
        for (String code : byteCodes) {
            try {
                final ClassReader reader  = new ClassReader(code);
                reader.accept(analyser, 0);
            } catch (Parser.ParseException ex) {
                ex.printStackTrace();
            }
        }
        return analyser.getMethods();
    }

    private static Set<AstMethod> indexJavaCodes(Set<String> javaCodes, AbstractVisitor visitor) throws FileNotFoundException, ParseException {
        for (String code : javaCodes) {
            System.out.println(code);
            final FileInputStream in = new FileInputStream(code);
            final CompilationUnit ast = JavaParser.parse(in);
            visitor.visit(ast, null);
        }
        return visitor.getMethods();
    }

    public static Searcher simple(String passToFolder) throws FileNotFoundException, ParseException {
        return simple(loadJava(passToFolder));
    }

    private static Searcher simple(Set<String> javaCodes) throws FileNotFoundException, ParseException {
        final Searcher self = new Searcher();
        final Set<AstMethod> astMethods = Searcher.indexJavaCodes(javaCodes, new SimpleAstVisitor());
        for (AstMethod method : astMethods) {
            final MethodDescription description = method.getDescription();
            self.indexedMethods.put(description, new HashSet<>());
            self.methods.put(description, new Pair<>(method, null));
        }
        return self;
    }

    public static Searcher normal(Set<String> javaCodes, Set<String> byteCodes) throws IOException, ParseException {
        final Searcher self = new Searcher();
        final Map<MethodDescription, Set<MethodDescription>> byteMethods = Searcher.indexByteCodes(byteCodes, new AsmClassAnalyser());
        final Set<AstMethod> astMethods = Searcher.indexJavaCodes(javaCodes, new AstVisitor());
        for (AstMethod method : astMethods) {
            final MethodDescription description = method.getDescription();
            final Set<MethodDescription> usages = new HashSet<>();
            if (byteMethods.containsKey(method.getDescription())) usages.addAll(byteMethods.get(method.getDescription()));
            self.indexedMethods.put(description, usages);
            self.methods.put(description, new Pair<>(method, null));
        }
        return self;
    }

    public void update(Set<DaikonMethod> methods) {
        for (DaikonMethod daikonMethod : methods) {
            final MethodDescription description = daikonMethod.getDescription();
            final AstMethod astMethod = this.methods.containsKey(description) ? this.methods.get(description).getValue0() : null;
            this.methods.put(description, new Pair<>(astMethod, daikonMethod));
        }
    }
}
