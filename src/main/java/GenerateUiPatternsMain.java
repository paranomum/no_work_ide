//import com.google.gson.Gson;
//import com.google.gson.GsonBuilder;
//import ui.frameworkmeta.PageObjectRegistry;
//import ui.frameworkmeta.patterns.UiMethodPatternGenerator;
//
//import java.io.OutputStreamWriter;
//import java.io.Writer;
//import java.nio.charset.StandardCharsets;
//import java.nio.file.Files;
//import java.nio.file.Path;
//import java.util.List;
//import java.util.Map;
//
//public class GenerateUiPatternsMain {
//
//	public static void main(String[] args) throws Exception {
//		Path srcRoot = Path.of("/Users/admin/Desktop/ui_tests/tests-ui/src/main/java");
//		PageObjectRegistry registry = new PageObjectRegistry(); // этот в твоём приложении
//
//		UiMethodPatternGenerator generator =
//				new UiMethodPatternGenerator(srcRoot, registry);
//
//		Map<String, Map<String, List<String>>> patterns =
//				generator.buildPatternsForAllPageObjects();
//
//		// 4. путь к resources
//		Path resourcesDir = Path.of("src/main/resources");
//		if (!Files.exists(resourcesDir)) {
//			Files.createDirectories(resourcesDir);
//		}
//
//		Path outFile = resourcesDir.resolve("ui-method-patterns.json");
//
//		// 5. сериализация в JSON
//		Gson gson = new GsonBuilder()
//				.setPrettyPrinting()
//				.create();
//
//		try (Writer writer = new OutputStreamWriter(
//				Files.newOutputStream(outFile), StandardCharsets.UTF_8)) {
//			gson.toJson(patterns, writer);
//		}
//
//		System.out.println("[GenerateUiPatternsMain] Patterns written to: "
//				+ outFile.toAbsolutePath());
//	}
//
//}
