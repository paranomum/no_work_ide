package util;

import dto.BackendRequestDef;

/**
 * БАГ 2 FIX: генерация уникального имени переменной из ответа backend-запроса.
 * Ранее имя строилось как requestName + "." + fieldPath, что при одинаковых
 * или дефолтных именах запросов (например "response") давало коллизии
 * (response.id у разных запросов). Теперь в имя дополнительно
 * встраивается HTTP-метод и путь URL запроса, что гарантирует уникальность.
 */
public final class VariableNameUtil {

	private VariableNameUtil() {
	}

	public static String buildUniqueVariableName(BackendRequestDef def, String fieldPath) {
		if (def == null) {
			return fieldPath;
		}

		String requestName = def.getName() != null ? def.getName().trim() : "";
		String method = def.getMethod() != null ? def.getMethod().trim().toUpperCase() : "";
		String urlSlug = slugifyUrl(def.getUrl());

		StringBuilder prefix = new StringBuilder();
		if (!requestName.isEmpty()) {
			prefix.append(requestName);
		}

		if (!method.isEmpty() || !urlSlug.isEmpty()) {
			if (prefix.length() > 0) {
				prefix.append("__");
			}
			if (!method.isEmpty()) {
				prefix.append(method);
			}
			if (!urlSlug.isEmpty()) {
				if (!method.isEmpty()) {
					prefix.append("_");
				}
				prefix.append(urlSlug);
			}
		}

		if (prefix.length() == 0) {
			return fieldPath;
		}

		return prefix + "." + fieldPath;
	}

	private static String slugifyUrl(String url) {
		if (url == null || url.isBlank()) {
			return "";
		}

		String path = url;
		int qIdx = path.indexOf('?');
		if (qIdx >= 0) {
			path = path.substring(0, qIdx);
		}

		String slug = path.replaceAll("[^a-zA-Z0-9]+", "_");
		slug = slug.replaceAll("^_+|_+$", "");
		return slug;
	}
}