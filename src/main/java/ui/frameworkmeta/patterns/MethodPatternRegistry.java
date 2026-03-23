package ui.frameworkmeta.patterns;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Загружает ui-method-patterns.json из ресурсов и даёт доступ к паттернам.
 * Формат JSON: Map<String pageClassFqn, Map<String methodName, List<String fieldDotAction>>>
 */
public class MethodPatternRegistry {

	private final Map<String, Map<String, List<String>>> patterns;

	public MethodPatternRegistry() {
		this("/ui-method-patterns.json");
	}

	public MethodPatternRegistry(String resourcePath) {
		this.patterns = loadPatterns(resourcePath);
	}

	@SuppressWarnings("unchecked")
	private Map<String, Map<String, List<String>>> loadPatterns(String resourcePath) {
		InputStream is = getClass().getResourceAsStream(resourcePath);
		if (is == null) {
			System.out.println("[MethodPatternRegistry] Resource not found: " + resourcePath);
			return Collections.emptyMap();
		}

		try (InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
			Gson gson = new Gson();
			Type type = new TypeToken<Map<String, Map<String, List<String>>>>() {}.getType();
			Map<String, Map<String, List<String>>> m = gson.fromJson(reader, type); // [web:82][web:80]
			System.out.println("[MethodPatternRegistry] Loaded patterns for "
					+ m.size() + " page object classes");
			return m;
		} catch (Exception e) {
			e.printStackTrace();
			System.out.println("[MethodPatternRegistry] Failed to load patterns: " + e.getMessage());
			return Collections.emptyMap();
		}
	}

	public Map<String, Map<String, List<String>>> getAllPatterns() {
		return patterns;
	}

	public Map<String, List<String>> getPatternsForPage(String pageClassFqn) {
		return patterns.getOrDefault(pageClassFqn, Collections.emptyMap());
	}

	public List<String> getPattern(String pageClassFqn, String methodName) {
		Map<String, List<String>> byMethod = getPatternsForPage(pageClassFqn);
		return byMethod.getOrDefault(methodName, Collections.emptyList());
	}

	public boolean hasPatterns() {
		return !patterns.isEmpty();
	}
}
