package util;

import java.util.ArrayList;
import java.util.List;

public class XPathSimilarity {

	public static boolean isProbablySame(String recorded, String poXpath) {
		if (recorded == null || recorded.isBlank() || poXpath == null || poXpath.isBlank()) {
			return false;
		}

		String a = normalize(recorded);
		String b = normalize(poXpath);

		// 1. если совпали полностью после нормализации — ок
		if (a.equals(b)) {
			return true;
		}

		// 2. если один содержит другой и длина отличается не сильно — тоже ок
		int lenA = a.length();
		int lenB = b.length();
		int maxLen = Math.max(lenA, lenB);

		if ((a.contains(b) || b.contains(a)) && Math.abs(lenA - lenB) < maxLen * 0.4) {
			return true;
		}

		// 3. проверим по «ключевым кускам» (теги + текст/contains(text))
		int score = 0;
		int possible = 0;

		// ключевые теги
		String[] importantTags = {
				"button", "input", "textarea", "mat-expansion-panel", "mat-expansion-panel-header",
				"label", "a", "div", "span"
		};
		for (String tag : importantTags) {
			possible++;
			if (a.contains("//" + tag) && b.contains("//" + tag)) {
				score++;
			}
		}

		// текстовые фрагменты внутри contains(text(), '...')
		List<String> textsA = extractTextLiterals(a);
		List<String> textsB = extractTextLiterals(b);

		for (String ta : textsA) {
			for (String tb : textsB) {
				possible++;
				if (ta.equals(tb)) {
					score++;
				}
			}
		}

		if (possible == 0) {
			return false;
		}

		double similarity = (double) score / possible;
		return similarity >= 0.5; // порог можно будет подправить
	}

	private static String normalize(String s) {
		String x = s.trim();
		// нижний регистр
		x = x.toLowerCase();
		// убираем двойные пробелы
		x = x.replaceAll("\\s+", " ");
		// убираем внешние скобки
		if (x.startsWith("(") && x.endsWith(")")) {
			x = x.substring(1, x.length() - 1).trim();
		}
		return x;
	}

	private static List<String> extractTextLiterals(String xpath) {
		List<String> result = new ArrayList<>();
		// очень простой парсер: ищем '...' в contains(text(), '...') и contains(., '...')
		java.util.regex.Matcher m = java.util.regex.Pattern
				.compile("contains\\([^)]*,'([^']+)'\\)")
				.matcher(xpath);
		while (m.find()) {
			result.add(m.group(1).trim().toLowerCase());
		}
		return result;
	}
}

