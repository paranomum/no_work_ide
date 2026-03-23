package ui.frameworkmeta.patterns;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.*;

import ui.frameworkmeta.PageObjectRegistry;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class UiMethodPatternGenerator {

	private final Path sourcesRoot;
	private final PageObjectRegistry pageObjectRegistry;

	public UiMethodPatternGenerator(Path sourcesRoot,
									PageObjectRegistry pageObjectRegistry) {
		this.sourcesRoot = sourcesRoot;
		this.pageObjectRegistry = pageObjectRegistry;
	}

	// pageClassFqn -> methodName -> list of "fieldName.actionCode"
	public Map<String, Map<String, List<String>>> buildPatternsForAllPageObjects() throws IOException {
		Map<String, Map<String, List<String>>> result = new LinkedHashMap<>();

		for (Class<?> poClass : pageObjectRegistry.getAllPageObjectClasses()) {
			Map<String, List<String>> methods = buildPatternsForClass(poClass);
			if (!methods.isEmpty()) {
				result.put(poClass.getName(), methods);
			}
		}

		return result;
	}

	public Map<String, List<String>> buildPatternsForClass(Class<?> pageObjectClass) throws IOException {
		Path sourcePath = resolveSourcePath(pageObjectClass);
		if (sourcePath == null || !Files.exists(sourcePath)) {
			System.out.println("[UiMethodPatternGenerator] Source file not found for "
					+ pageObjectClass.getName() + " at " + sourcePath);
			return Collections.emptyMap();
		}

		CompilationUnit cu = StaticJavaParser.parse(sourcePath); // [web:66][web:67]

		Optional<ClassOrInterfaceDeclaration> clazzOpt = cu
				.findAll(ClassOrInterfaceDeclaration.class).stream()
				.filter(c -> c.getNameAsString().equals(pageObjectClass.getSimpleName()))
				.findFirst();

		if (clazzOpt.isEmpty()) {
			System.out.println("[UiMethodPatternGenerator] Class decl not found in file: "
					+ pageObjectClass.getName());
			return Collections.emptyMap();
		}

		ClassOrInterfaceDeclaration clazz = clazzOpt.get();

		Map<String, List<String>> methodPatterns = new LinkedHashMap<>();

		for (MethodDeclaration method : clazz.getMethods()) {
			if (!method.getBody().isPresent()) {
				continue;
			}

			String methodName = method.getNameAsString();

			List<MethodCallExpr> calls = method.getBody().get().findAll(MethodCallExpr.class);

			List<String> pattern = new ArrayList<>();

			for (MethodCallExpr call : calls) {
				Optional<Expression> scopeOpt = call.getScope();
				if (scopeOpt.isEmpty()) {
					continue;
				}

				Expression scope = scopeOpt.get();
				String fieldName = null;

				if (scope instanceof FieldAccessExpr fa) {
					fieldName = fa.getNameAsString(); // this.loginField.setValue
				} else if (scope instanceof NameExpr ne) {
					fieldName = ne.getNameAsString(); // loginField.setValue
				} else {
					continue;
				}

				String action = call.getNameAsString();
				pattern.add(fieldName + "." + action);
			}

			if (!pattern.isEmpty()) {
				methodPatterns.put(methodName, pattern);
				System.out.println("[UiMethodPatternGenerator] "
						+ pageObjectClass.getSimpleName() + "." + methodName
						+ " -> " + pattern);
			}
		}

		return methodPatterns;
	}

	private Path resolveSourcePath(Class<?> clazz) {
		String fqn = clazz.getName();
		String rel = fqn.replace('.', File.separatorChar) + ".java";
		return sourcesRoot.resolve(rel).normalize();
	}
}
