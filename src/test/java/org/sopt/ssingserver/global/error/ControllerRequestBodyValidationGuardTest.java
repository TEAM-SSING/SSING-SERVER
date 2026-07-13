package org.sopt.ssingserver.global.error;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.source.tree.AnnotationTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreePathScanner;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;

class ControllerRequestBodyValidationGuardTest {

    private static final Path DOMAIN_SOURCE_ROOT = Path.of("src/main/java/org/sopt/ssingserver/domain");

    @Test
    void runtime_Controller의_RequestBody는_Valid로_검증한다() throws IOException {
        List<Path> controllerFiles = findControllerFiles();

        assertThat(controllerFiles)
                .as("검증 대상 runtime Controller 파일이 없으면 @Valid 정책 검증이 무의미합니다.")
                .isNotEmpty();

        List<String> violations = controllerFiles.stream()
                .flatMap(ControllerRequestBodyValidationGuardTest::requestBodyValidationViolations)
                .toList();

        assertThat(violations)
                .as("""
                        요청 body DTO는 Controller에서 @Valid로 검증해야 합니다.
                        아래 @RequestBody 파라미터에 @Valid를 추가하세요.
                        위반 목록:
                        %s
                        """, String.join(System.lineSeparator(), violations))
                .isEmpty();
    }

    private static List<Path> findControllerFiles() throws IOException {
        try (Stream<Path> paths = Files.walk(DOMAIN_SOURCE_ROOT)) {
            return paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(ControllerRequestBodyValidationGuardTest::isRuntimeController)
                    .toList();
        }
    }

    private static boolean isRuntimeController(Path path) {
        String normalizedPath = path.toString().replace('\\', '/');
        return normalizedPath.contains("/controller/")
                && !normalizedPath.contains("/controller/docs/");
    }

    private static Stream<String> requestBodyValidationViolations(Path path) {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException("JDK Java compiler를 찾지 못했습니다.");
        }

        List<String> violations = new ArrayList<>();
        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, null)) {
            Iterable<? extends JavaFileObject> sourceFiles = fileManager.getJavaFileObjects(path.toFile());
            JavacTask task = (JavacTask) compiler.getTask(
                    null,
                    fileManager,
                    null,
                    List.of("-proc:none"),
                    null,
                    sourceFiles
            );

            for (CompilationUnitTree compilationUnit : task.parse()) {
                new TreePathScanner<Void, Void>() {
                    @Override
                    public Void visitMethod(MethodTree methodTree, Void unused) {
                        methodTree.getParameters().stream()
                                .filter(parameter -> hasAnnotation(parameter, "RequestBody"))
                                .filter(parameter -> !hasAnnotation(parameter, "Valid"))
                                .map(parameter -> path + "#" + methodTree.getName()
                                        + "(" + parameter.getName() + ") - @Valid 누락")
                                .forEach(violations::add);
                        return super.visitMethod(methodTree, unused);
                    }
                }.scan(compilationUnit, null);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Controller 소스 파일을 읽지 못했습니다: " + path, exception);
        }
        return violations.stream();
    }

    private static boolean hasAnnotation(VariableTree parameter, String simpleName) {
        return parameter.getModifiers().getAnnotations().stream()
                .map(AnnotationTree::getAnnotationType)
                .map(Object::toString)
                .anyMatch(annotationName -> annotationName.equals(simpleName)
                        || annotationName.endsWith("." + simpleName));
    }
}
