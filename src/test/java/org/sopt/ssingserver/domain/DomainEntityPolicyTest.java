package org.sopt.ssingserver.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.source.tree.AnnotationTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreePathScanner;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;

class DomainEntityPolicyTest {

    private static final Path MAIN_SOURCE_ROOT = Path.of("src/main/java");
    private static final Path DOMAIN_SOURCE_ROOT = MAIN_SOURCE_ROOT.resolve("org/sopt/ssingserver/domain");
    private static final Set<String> FORBIDDEN_LOMBOK_ANNOTATIONS = Set.of(
            "lombok.Setter",
            "lombok.Data",
            "lombok.Builder",
            "lombok.experimental.SuperBuilder"
    );

    @Test
    void 도메인_Entity는_protected_기본생성자를_사용하고_public_setter를_노출하지_않는다() throws IOException {
        List<Class<?>> entityClasses = findEntityClasses();

        assertThat(entityClasses)
                .as("검증 대상 도메인 Entity가 없으면 정책 검증이 무의미합니다.")
                .isNotEmpty();

        List<String> violations = entityClasses.stream()
                .flatMap(DomainEntityPolicyTest::constructorAndSetterViolations)
                .toList();

        assertThat(violations)
                .as("Entity는 protected 기본 생성자를 사용하고 public setter를 노출하지 않습니다: %s", violations)
                .isEmpty();
    }

    @Test
    void 도메인_Entity의_enum_필드는_EnumType_STRING_annotation을_선언한다() throws IOException {
        List<String> violations = findEntityClasses().stream()
                .flatMap(DomainEntityPolicyTest::enumMappingViolations)
                .toList();

        assertThat(violations)
                .as("Entity enum 필드는 @Enumerated(EnumType.STRING)을 선언해야 합니다: %s", violations)
                .isEmpty();
    }

    @Test
    void 도메인_Entity는_Lombok_Setter_Data_Builder를_사용하지_않는다() throws IOException {
        List<String> violations = findEntitySourceFiles().stream()
                .flatMap(DomainEntityPolicyTest::forbiddenLombokAnnotations)
                .toList();

        assertThat(violations)
                .as("Entity에서는 @Setter, @Data, @Builder를 사용하지 않습니다: %s", violations)
                .isEmpty();
    }

    private static List<Class<?>> findEntityClasses() throws IOException {
        return findEntitySourceFiles().stream()
                .map(DomainEntityPolicyTest::loadClass)
                .filter(type -> type.isAnnotationPresent(Entity.class))
                .toList();
    }

    private static List<Path> findEntitySourceFiles() throws IOException {
        try (Stream<Path> paths = Files.walk(DOMAIN_SOURCE_ROOT)) {
            return paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(DomainEntityPolicyTest::isEntityPackageSource)
                    .toList();
        }
    }

    private static boolean isEntityPackageSource(Path path) {
        String normalizedPath = path.toString().replace('\\', '/');
        return normalizedPath.contains("/entity/");
    }

    private static Class<?> loadClass(Path sourcePath) {
        String relativePath = MAIN_SOURCE_ROOT.relativize(sourcePath).toString();
        String className = relativePath
                .substring(0, relativePath.length() - ".java".length())
                .replace(File.separatorChar, '.');
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException exception) {
            throw new IllegalStateException("Entity 클래스를 불러오지 못했습니다: " + className, exception);
        }
    }

    private static Stream<String> constructorAndSetterViolations(Class<?> entityClass) {
        List<String> violations = new ArrayList<>();
        try {
            Constructor<?> constructor = entityClass.getDeclaredConstructor();
            if (!Modifier.isProtected(constructor.getModifiers())) {
                violations.add(entityClass.getName() + " - 기본 생성자가 protected가 아님");
            }
        } catch (NoSuchMethodException exception) {
            violations.add(entityClass.getName() + " - 기본 생성자 없음");
        }

        Arrays.stream(entityClass.getMethods())
                .filter(DomainEntityPolicyTest::isPublicSetter)
                .map(method -> entityClass.getName() + "#" + method.getName() + " - public setter")
                .forEach(violations::add);
        return violations.stream();
    }

    private static boolean isPublicSetter(Method method) {
        return Modifier.isPublic(method.getModifiers())
                && method.getReturnType() == void.class
                && method.getParameterCount() == 1
                && method.getName().length() > 3
                && method.getName().startsWith("set")
                && Character.isUpperCase(method.getName().charAt(3));
    }

    private static Stream<String> enumMappingViolations(Class<?> entityClass) {
        return Arrays.stream(entityClass.getDeclaredFields())
                .filter(field -> !Modifier.isStatic(field.getModifiers()))
                .flatMap(field -> enumMappingViolation(entityClass, field));
    }

    private static Stream<String> enumMappingViolation(Class<?> entityClass, Field field) {
        Enumerated enumerated = field.getAnnotation(Enumerated.class);
        if (enumerated != null && enumerated.value() != EnumType.STRING) {
            return Stream.of(entityClass.getName() + "#" + field.getName() + " - EnumType.STRING이 아님");
        }
        if (storesEnumValue(field) && enumerated == null) {
            return Stream.of(entityClass.getName() + "#" + field.getName() + " - @Enumerated 누락");
        }
        return Stream.empty();
    }

    private static boolean storesEnumValue(Field field) {
        if (field.getType().isEnum()) {
            return true;
        }
        if (!field.isAnnotationPresent(ElementCollection.class)) {
            return false;
        }
        Type genericType = field.getGenericType();
        if (!(genericType instanceof ParameterizedType parameterizedType)) {
            return false;
        }
        return Arrays.stream(parameterizedType.getActualTypeArguments())
                .anyMatch(type -> type instanceof Class<?> elementType && elementType.isEnum());
    }

    private static Stream<String> forbiddenLombokAnnotations(Path path) {
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
                Set<String> imports = compilationUnit.getImports().stream()
                        .map(importTree -> importTree.getQualifiedIdentifier().toString())
                        .collect(Collectors.toSet());
                new TreePathScanner<Void, Void>() {
                    @Override
                    public Void visitAnnotation(AnnotationTree annotationTree, Void unused) {
                        String annotationName = annotationTree.getAnnotationType().toString();
                        if (isForbiddenLombokAnnotation(annotationName, imports)) {
                            violations.add(path + " - @" + annotationName);
                        }
                        return super.visitAnnotation(annotationTree, unused);
                    }
                }.scan(compilationUnit, null);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Entity 소스 파일을 읽지 못했습니다: " + path, exception);
        }
        return violations.stream();
    }

    private static boolean isForbiddenLombokAnnotation(String annotationName, Set<String> imports) {
        if (FORBIDDEN_LOMBOK_ANNOTATIONS.contains(annotationName)) {
            return true;
        }
        if (annotationName.contains(".")) {
            return false;
        }
        return FORBIDDEN_LOMBOK_ANNOTATIONS.stream()
                .filter(qualifiedName -> qualifiedName.endsWith("." + annotationName))
                .anyMatch(qualifiedName -> imports.contains(qualifiedName)
                        || imports.contains(qualifiedName.substring(0, qualifiedName.lastIndexOf('.')) + ".*"));
    }
}
