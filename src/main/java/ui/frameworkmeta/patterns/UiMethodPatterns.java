package ui.frameworkmeta.patterns;

import java.util.List;
import java.util.Map;

public class UiMethodPatterns {

	// pageClassFqn -> methodName -> list of "fieldName.actionCode"
	private Map<String, Map<String, List<String>>> patterns;

	public UiMethodPatterns(Map<String, Map<String, List<String>>> patterns) {
		this.patterns = patterns;
	}

	public Map<String, Map<String, List<String>>> getPatterns() {
		return patterns;
	}
}
