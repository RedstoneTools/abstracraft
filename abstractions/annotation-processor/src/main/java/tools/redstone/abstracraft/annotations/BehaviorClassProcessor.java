package tools.redstone.abstracraft.annotations;

import com.google.auto.service.AutoService;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.lang.model.type.TypeKind;
import javax.tools.Diagnostic;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

@AutoService(Processor.class)
public class BehaviorClassProcessor extends AbstractProcessor {
    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return Set.of(BehaviorClass.class.getCanonicalName());
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        var behaviorClasses = getBehaviorClasses(roundEnv);

        createMethodClasses(behaviorClasses);

        return true;
    }

    private Set<TypeElement> getBehaviorClasses(RoundEnvironment roundEnv) {
        return roundEnv.getElementsAnnotatedWith(BehaviorClass.class).stream()
                .filter(element -> element.getKind() == ElementKind.CLASS)
                .map(element -> (TypeElement) element)
                .collect(Collectors.toSet());
    }

    private void createMethodClasses(Set<TypeElement> behaviorClasses) {
        for (var behaviorClass : behaviorClasses) {
            createMethodClasses(behaviorClass);
        }
    }

    private void createMethodClasses(TypeElement behaviorClass) {
        if (!assertBehaviorClassChecks(behaviorClass)) {
            return;
        }

        var methodsAndOverloads = getMethodsAndOverloads(behaviorClass);

        for (var methodAndOverloads : methodsAndOverloads) {
            createMethodClass(behaviorClass, methodAndOverloads);
        }
    }

    private boolean assertBehaviorClassChecks(TypeElement behaviorClass) {
        // Should be public abstract
        if (!behaviorClass.getModifiers().contains(Modifier.PUBLIC)
         || !behaviorClass.getModifiers().contains(Modifier.ABSTRACT)) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "Classes annotated with @" + BehaviorClass.class.getSimpleName() + " should be public abstract, " + behaviorClass.getSimpleName() + " is not.", behaviorClass);

            return false;
        }

        // Should only contain protected abstract methods
        // TODO: Implement this check and refactor this method

        return true;
    }

    private Set<Set<ExecutableElement>> getMethodsAndOverloads(TypeElement behaviorClass) {
        var methodMap = behaviorClass.getEnclosedElements().stream()
                .filter(element -> element.getKind() == ElementKind.METHOD)
                .map(element -> (ExecutableElement) element)
                .collect(Collectors.groupingBy(
                        this::getMethodName,
                        Collectors.toSet()
                ));

        return new HashSet<>(methodMap.values());
    }

    private void createMethodClass(TypeElement behaviorClass, Set<ExecutableElement> methodAndOverloads) {
        var behaviorClassName = getClassName(behaviorClass);
        var behaviorClassPackageName = getPackageName(behaviorClass);
        var methodName = getMethodName(methodAndOverloads);

        var generatedClassName = behaviorClassName + "_" + methodName;
        var generatedFileName = behaviorClassPackageName + "." + generatedClassName;

        var codeBuilder = new StringBuilder()
                .append("package ").append(behaviorClassPackageName).append(";\n")
                .append("\n")
                .append("public final class ").append(generatedClassName).append(" extends tools.redstone.abstracraft.abstractions.Method<").append(behaviorClassName).append(", ").append(generatedClassName).append("> {\n")
                .append("    public ").append(generatedClassName).append("() {\n")
                .append("        super();\n")
                .append("    }\n")
                .append("\n")
                .append("    private ").append(generatedClassName).append("(").append(behaviorClassName).append(" self) {\n")
                .append("        super(self);\n")
                .append("    }\n")
                .append("\n")
                .append("    @Override\n")
                .append("    protected ").append(generatedClassName).append(" withSelf(").append(behaviorClassName).append(" self) {\n")
                .append("        return new ").append(generatedClassName).append("(self);\n")
                .append("    }\n");


        for (var method : methodAndOverloads) {
            appendBehaviorMethod(codeBuilder, method);
        }

        codeBuilder.append("}\n");

        try (var sourceWriter = processingEnv.getFiler().createSourceFile(generatedFileName, behaviorClass).openWriter()) {
            sourceWriter.write(codeBuilder.toString());
        } catch (IOException e) {
            var message = "Failed to create method class for method \"%s\" in class \"%s\": %s"
                    .formatted(methodName, behaviorClassName, e.getMessage());

            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, message);
        }
    }

    private void appendBehaviorMethod(StringBuilder codeBuilder, ExecutableElement method) {
        // TODO: Add support for type parameters, e.g.:
        //   public <T extends SomeClass> void doStuff(T x, Set<T> y)

        codeBuilder.append("\n    public ").append(method.getReturnType()).append(" call(");

        for (var parameter : method.getParameters()) {
            codeBuilder.append(parameter.asType()).append(" ").append(parameter.getSimpleName()).append(", ");
        }

        if (!method.getParameters().isEmpty()) {
            codeBuilder.setLength(codeBuilder.length() - 2);
        }

        codeBuilder.append(") {\n")
                .append("        ");

        if (method.getReturnType().getKind() != TypeKind.VOID) {
            codeBuilder.append("return ");
        }

        codeBuilder.append("getSelf().").append(method.getSimpleName()).append("(");

        for (var parameter : method.getParameters()) {
            codeBuilder.append(parameter.getSimpleName()).append(", ");
        }

        if (!method.getParameters().isEmpty()) {
            codeBuilder.setLength(codeBuilder.length() - 2);
        }

        codeBuilder.append(");\n")
                .append("    }\n");
    }

    private String getPackageName(TypeElement typeElement) {
        Element enclosingElement = typeElement.getEnclosingElement();

        if (enclosingElement instanceof PackageElement packageElement) {
            return packageElement.getQualifiedName().toString();
        }

        return "";
    }

    private String getClassName(TypeElement behaviorClass) {
        return behaviorClass.getSimpleName().toString();
    }

    private String getMethodName(ExecutableElement method) {
        return method.getSimpleName().toString();
    }

    private String getMethodName(Set<ExecutableElement> methodAndOverloads) {
        return getMethodName(methodAndOverloads.stream().findFirst().orElseThrow());
    }
}
