package ui.action;

import dto.ActionRecord;
import dto.AtomicStep;
import dto.GeneratedStep;
import model.ElementType;
import model.UserAction;
import ui.frameworkmeta.PageObjectIntrospector;
import ui.frameworkmeta.PageObjectMatcher;
import ui.frameworkmeta.PageObjectRegistry;
import ui.frameworkmeta.patterns.MethodPatternRegistry;

import java.util.*;

import static ru.rt.iqhr.framework.util.XPathUtils.isProbablyXPath;

public class TestGeneratorService {

	private final PageObjectRegistry pageObjectRegistry;
	private final MethodPatternRegistry methodPatternRegistry;

	public TestGeneratorService(PageObjectRegistry pageObjectRegistry) {
		this.pageObjectRegistry = pageObjectRegistry;
		this.methodPatternRegistry = new MethodPatternRegistry();
	}

	public String generateJavaTestClass(List<ActionRecord> records) {
		try {
			if (methodPatternRegistry.hasPatterns()) {
				// Новый путь с паттернами
				return generateWithPatterns(records);
			}
		} catch (Exception e) {
			e.printStackTrace();
			// Логируем, но не роняем генерацию
			System.out.println("[TestGeneratorService] Pattern-based generation failed, fallback to legacy: "
					+ e.getMessage());
		}

		// Старый проверенный путь
		return generateLegacy(records);
	}

	private String generateWithPatterns(List<ActionRecord> records) {
		// 1) строим атомарные шаги + собираем usedPageObjectClasses
		JavaBuildResult buildResult = new JavaBuildResult();
		List<AtomicStep> atomicSteps = buildAtomicSteps(records, buildResult);

		// 2) склеиваем по метод‑паттернам
		List<GeneratedStep> steps = compressWithMethodPatterns(atomicSteps);

		// 3) собираем тело теста
		StringBuilder body = new StringBuilder();

		// --- объявления PageObject‑переменных ---
		for (String fqcn : buildResult.usedPageObjectClasses) {
			String simple = fqcn.substring(fqcn.lastIndexOf('.') + 1);
			String varName = decapitalize(simple);
			body.append("        ")
					.append(simple)
					.append(" ")
					.append(varName)
					.append(" = new ")
					.append(simple)
					.append("();\n");
		}
		if (!buildResult.usedPageObjectClasses.isEmpty()) {
			body.append("\n");
		}

		// --- шаги ---
		for (GeneratedStep gs : steps) {
			if (gs.kind == GeneratedStep.Kind.METHOD_CALL) {
				body.append("        ")
						.append(gs.pageVarName)
						.append(".")
						.append(gs.methodName)
						.append("(");
				for (int idx = 0; idx < gs.methodArgs.size(); idx++) {
					if (idx > 0) body.append(", ");
					String v = gs.methodArgs.get(idx);
					if (v == null) {
						body.append("null");
					} else {
						body.append("\"").append(v.replace("\"", "\\\"")).append("\"");
					}
				}
				body.append(");\n");
			} else {
				String line = buildJavaLineFromAtomic(gs.atomic);
				body.append("        ").append(line).append("\n");
			}
		}

		return wrapIntoTestClass(body.toString());
	}

	private String generateLegacy(List<ActionRecord> records) {
		JavaBuildResult buildResult = buildJavaLinesFromRecords(records);

		StringBuilder body = new StringBuilder();

		// объявления PageObject-переменных
		for (String fqcn : buildResult.usedPageObjectClasses) {
			String simple = fqcn.substring(fqcn.lastIndexOf('.') + 1);
			String varName = decapitalize(simple);
			body.append("        ")
					.append(simple)
					.append(" ")
					.append(varName)
					.append(" = new ")
					.append(simple)
					.append("();\n");
		}
		if (!buildResult.usedPageObjectClasses.isEmpty()) {
			body.append("\n");
		}

		// сами шаги
		for (String line : buildResult.lines) {
			body.append("        ").append(line).append("\n");
		}

		return wrapIntoTestClass(body.toString());
	}

	private String wrapIntoTestClass(String body) {
		return "@Tag(\"\")\n" +
				"@TestClassIQHR(name = \"\")\n" +
				"public class GeneratedTestCase {\n" +
				"\n" +
				"    @TestIQHR(name = \"\", tmsLink = \"IQHR-T\")\n" +
				"    public void generatedTest() {\n" +
				body +
				"    }\n" +
				"}\n";
	}

	private List<AtomicStep> buildAtomicSteps(List<ActionRecord> records, JavaBuildResult result) {
		List<AtomicStep> steps = new ArrayList<>();

		for (ActionRecord rec : records) {
			String actionCode  = rec.getAction();
			String selector    = rec.getSelector();
			String value       = rec.getValue();
			String comment     = rec.getComment();
			String elementType = rec.getElementType();
			String xpath       = rec.getXpath();
			String name        = rec.getName();
			String indexStr    = rec.getIndex();
			String byXpathStr  = rec.getByXpath();
			String pageUrlPath = rec.getPageUrlPath();

			if (actionCode == null || actionCode.isBlank()) {
				continue;
			}

			String actionLower = actionCode.toLowerCase();
			boolean specialAction = actionLower.contains("pause")
					|| actionLower.contains("waitloadingpage")
					|| actionLower.contains("filldata")
					|| actionLower.contains("auth")
					|| actionLower.contains("specialaction")
					|| actionLower.contains("switchtab")
					|| actionLower.contains("open");

			if (specialAction) {
				// спец‑шаги пока оставляем как «старую» строку,
				// оборачиваем в AtomicStep без pageClass/fieldName
				AtomicStep s = new AtomicStep();
				s.pageClassName = null;
				s.pageVarName = null;
				s.fieldName = null;
				s.actionCode = null;
				s.value = null;
				s.comment = appendSpecialAction(actionCode, value, comment);
				steps.add(s);
				continue;
			}

			// обычные шаги
			PageObjectIntrospector.Descriptor match = null;
			String javaWebElement = null;
			String pageClassFqn = null;
			String pageVar = null;
			String fieldName = null;

			if (elementType != null && name != null && !name.isBlank()) {

				if (pageUrlPath != null && !pageUrlPath.isBlank()) {
					List<PageObjectIntrospector.Descriptor> descriptors =
							pageObjectRegistry.getElementsForPath(pageUrlPath);

					match = PageObjectMatcher.findMatch(descriptors, elementType, name, xpath);
				}

				if (match == null) {
					List<PageObjectIntrospector.Descriptor> allDescriptors =
							pageObjectRegistry.getAllPageObjectDescriptors();
					match = PageObjectMatcher.findMatch(allDescriptors, elementType, name, xpath);
				}

				if (match != null) {
					String pageClassSimpleName = match.pageSimpleName;
					pageVar = decapitalize(pageClassSimpleName);
					String getterName = "get" + capitalize(match.fieldName);
					javaWebElement = pageVar + "." + getterName + "()";

					pageClassFqn = match.pageClass.getName();
					fieldName = match.fieldName;

					result.usedPageObjectClasses.add(match.pageClass.getName());
				}
			}

			if (javaWebElement == null) {
				javaWebElement = buildRawJavaWebElement(
						elementType, selector, xpath, name, indexStr, byXpathStr
				);
			}

			AtomicStep step = new AtomicStep();
			step.pageClassName = pageClassFqn;
			step.pageVarName   = pageVar;
			step.fieldName     = fieldName;
			step.actionCode    = actionCode;
			step.value         = value;
			step.comment       = comment;
			step.javaWebElement = javaWebElement;

			steps.add(step);
		}

		return steps;
	}


	private JavaBuildResult buildJavaLinesFromRecords(List<ActionRecord> records) {
		JavaBuildResult result = new JavaBuildResult();

		for (ActionRecord rec : records) {
			String actionCode  = rec.getAction();
			String selector    = rec.getSelector();
			String value       = rec.getValue();
			String comment     = rec.getComment();
			String elementType = rec.getElementType();
			String xpath       = rec.getXpath();
			String name        = rec.getName();
			String indexStr    = rec.getIndex();
			String byXpathStr  = rec.getByXpath();
			String pageUrlPath = rec.getPageUrlPath();

			if (actionCode == null || actionCode.isBlank()) {
				continue;
			}

			String actionLower = actionCode.toLowerCase();
			boolean isValueAction = !actionLower.contains("click")
					&& !actionLower.contains("filldate");

			boolean specialAction = actionLower.contains("pause")
					|| actionLower.contains("waitloadingpage")
					|| actionLower.contains("filldata")
					|| actionLower.contains("auth")
					|| actionLower.contains("specialaction")
					|| actionLower.contains("switchtab")
					|| actionLower.contains("open");

			if (specialAction) {
				result.lines.add(appendSpecialAction(actionCode, value, comment));
				continue;
			}

			String javaWebElement = null;

			// матчинг на PageObject
			if (elementType != null && name != null && !name.isBlank()) {

				PageObjectIntrospector.Descriptor match = null;

				if (pageUrlPath != null && !pageUrlPath.isBlank()) {
					List<PageObjectIntrospector.Descriptor> descriptors =
							pageObjectRegistry.getElementsForPath(pageUrlPath);

					match = PageObjectMatcher.findMatch(descriptors, elementType, name, xpath);
				}

				if (match == null) {
					List<PageObjectIntrospector.Descriptor> allDescriptors =
							pageObjectRegistry.getAllPageObjectDescriptors();
					match = PageObjectMatcher.findMatch(allDescriptors, elementType, name, xpath);
				}

				if (match != null) {
					String pageClassSimpleName = match.pageSimpleName;
					String pageVar = decapitalize(pageClassSimpleName);
					String getterName = "get" + capitalize(match.fieldName);
					javaWebElement = pageVar + "." + getterName + "()";

					result.usedPageObjectClasses.add(match.pageClass.getName());
				}
			}

			if (javaWebElement == null) {
				javaWebElement = buildRawJavaWebElement(
						elementType, selector, xpath, name, indexStr, byXpathStr
				);
			}

			StringBuilder sb = new StringBuilder();
			sb.append(javaWebElement).append(".").append(actionCode).append("(");

			if (value != null && !value.isBlank() && isValueAction) {
				String safeValue = value.replace("\"", "\\\"");
				sb.append("\"").append(safeValue).append("\"");
			}

			sb.append(");");

			if (comment != null && !comment.isBlank()) {
				sb.append(" // ").append(comment);
			}

			result.lines.add(sb.toString());
		}

		return result;
	}

	private String buildJavaLineFromAtomic(AtomicStep s) {
		// спец‑шаги, которые мы закодировали в comment как готовую строку
		if (s.pageClassName == null && s.javaWebElement == null && s.comment != null
				&& s.comment.startsWith("open(") /* условие можно сделать аккуратнее */) {
			return s.comment;
		}

		String actionCode = s.actionCode;
		String value      = s.value;
		String comment    = s.comment;

		String actionLower = actionCode.toLowerCase();
		boolean isValueAction = !actionLower.contains("click")
				&& !actionLower.contains("filldate");

		StringBuilder sb = new StringBuilder();
		sb.append(s.javaWebElement).append(".").append(actionCode).append("(");

		if (value != null && !value.isBlank() && isValueAction) {
			String safeValue = value.replace("\"", "\\\"");
			sb.append("\"").append(safeValue).append("\"");
		}

		sb.append(");");

		if (comment != null && !comment.isBlank()) {
			sb.append(" // ").append(comment);
		}

		return sb.toString();
	}


	private String buildRawJavaWebElement(
			String javaClass, String selector, String xpath,
			String name, String indexStr, String byXpathStr
	) {
		String jc = javaClass != null ? javaClass : "Field";
		String javaWebElement = "new " + jc + "(\"";

		boolean hasName = name != null && !name.isBlank();
		boolean byXpath = "true".equalsIgnoreCase(byXpathStr);

		if (hasName) {
			if (!byXpath) {
				Integer index = null;
				if (indexStr != null && !indexStr.isBlank()) {
					try {
						index = Integer.parseInt(indexStr.trim());
					} catch (NumberFormatException ignore) {}
				}

				if (index != null && index > 1) {
					javaWebElement = javaWebElement + name + "\", " + index + ")";
				} else {
					javaWebElement = javaWebElement + name + "\")";
				}
			} else {
				String safeXpath = xpath == null ? "" : xpath.replace("\"", "\\\"");
				javaWebElement = javaWebElement + name + "\", $x(\"" + safeXpath + "\"))";
			}
		} else {
			String safeSelector = selector == null ? "" : selector.replace("\"", "\\\"");
			if (isProbablyXPath(selector))
				javaWebElement = javaWebElement + jc + "\", $x(\"" + safeSelector + "\"))";
			else {
				if (ActionFileService.hasCommaSpacesDigitAndNoLettersAfter(selector)) {
					String[] selectors = selector.trim().split(",");
					javaWebElement = javaWebElement + selectors[0] + "\", " + selectors[1] + ")";
				} else {
					javaWebElement = javaWebElement + selector + "\")";
				}
			}
		}

		return javaWebElement;
	}

	private String appendSpecialAction(String actionCode, String value, String comment) {
		String actionLower = actionCode == null ? "" : actionCode.toLowerCase();

		boolean isParamAction =
				"open".equals(actionLower)
						|| "auth".equals(actionLower)
						|| "waitloadingpage".equals(actionLower)
						|| "pause".equals(actionLower);

		StringBuilder sb = new StringBuilder();
		sb.append(actionCode);
		sb.append("(");

		if (isParamAction && value != null && !value.isBlank()) {
			if ("waitloadingpage".equals(actionLower) || "pause".equals(actionLower)) {
				sb.append("\"")
						.append(value.replaceAll("[\\D]", ""))
						.append("\"");
			} else {
				sb.append("\"")
						.append(value.replace("\"", "\\\""))
						.append("\"");
			}
		}

		sb.append(");");

		if (comment != null && !comment.isBlank() || (value != null && !value.isBlank() && !isParamAction)) {
			sb.append(" // ");
			if (isParamAction) {
				if (comment != null && !comment.isBlank()) {
					sb.append(comment);
				}
			} else {
				boolean hasComment = comment != null && !comment.isBlank();
				if (hasComment) {
					sb.append(comment);
				}
				if (value != null && !value.isBlank()) {
					if (hasComment) {
						sb.append(", ");
					}
					sb.append(value);
				}
			}
		}

		return sb.toString();
	}

	private List<GeneratedStep> compressWithMethodPatterns(List<AtomicStep> atomicSteps) {
		if (!methodPatternRegistry.hasPatterns()) {
			// нет json — просто оборачиваем всё как ATOMIC
			List<GeneratedStep> res = new ArrayList<>();
			for (AtomicStep s : atomicSteps) {
				GeneratedStep gs = new GeneratedStep();
				gs.kind = GeneratedStep.Kind.ATOMIC;
				gs.atomic = s;
				res.add(gs);
			}
			return res;
		}

		Map<String, Map<String, List<String>>> allPatterns = methodPatternRegistry.getAllPatterns();

		List<GeneratedStep> result = new ArrayList<>();
		int i = 0;

		while (i < atomicSteps.size()) {
			AtomicStep current = atomicSteps.get(i);
			if (current.pageClassName == null) {
				// шаг не привязан к PageObject — не склеиваем
				GeneratedStep gs = new GeneratedStep();
				gs.kind = GeneratedStep.Kind.ATOMIC;
				gs.atomic = current;
				result.add(gs);
				i++;
				continue;
			}

			Map<String, List<String>> pagePatterns =
					allPatterns.getOrDefault(current.pageClassName, Collections.emptyMap());

			if (pagePatterns.isEmpty()) {
				// для этого PageObject нет паттернов
				GeneratedStep gs = new GeneratedStep();
				gs.kind = GeneratedStep.Kind.ATOMIC;
				gs.atomic = current;
				result.add(gs);
				i++;
				continue;
			}

			// пытаемся найти подходящий метод‑паттерн, начиная с позиции i
			Match bestMatch = null;

			for (Map.Entry<String, List<String>> e : pagePatterns.entrySet()) {
				String methodName = e.getKey();
				List<String> pattern = e.getValue();
				Match m = tryMatchPattern(methodName, pattern, atomicSteps, i);
				if (m != null && m.length > 0) {
					// для простоты берём первый матч; можно добавить выбор «самого длинного»
					bestMatch = m;
					break;
				}
			}

			if (bestMatch != null) {
				GeneratedStep gs = new GeneratedStep();
				gs.kind = GeneratedStep.Kind.METHOD_CALL;
				gs.pageClassName = current.pageClassName;
				gs.pageVarName = current.pageVarName;
				gs.methodName = bestMatch.methodName;
				gs.methodArgs = bestMatch.args;

				result.add(gs);
				i += bestMatch.length;
			} else {
				GeneratedStep gs = new GeneratedStep();
				gs.kind = GeneratedStep.Kind.ATOMIC;
				gs.atomic = current;
				result.add(gs);
				i++;
			}
		}

		return result;
	}

	private String decapitalize(String s) {
		if (s == null || s.isEmpty()) return s;
		return Character.toLowerCase(s.charAt(0)) + s.substring(1);
	}

	private String capitalize(String s) {
		if (s == null || s.isEmpty()) return s;
		return Character.toUpperCase(s.charAt(0)) + s.substring(1);
	}

	private static class JavaBuildResult {
		List<String> lines = new ArrayList<>();
		Set<String> usedPageObjectClasses = new LinkedHashSet<>();
	}

	private static class Match {
		String methodName;
		int length;
		List<String> args;
	}

	private Match tryMatchPattern(String methodName,
								  List<String> pattern,
								  List<AtomicStep> steps,
								  int startIdx) {
		if (pattern == null || pattern.isEmpty()) return null;
		if (startIdx + pattern.size() > steps.size()) return null;

		List<String> args = new ArrayList<>();

		for (int k = 0; k < pattern.size(); k++) {
			String pat = pattern.get(k);
			String[] parts = pat.split("\\.");
			if (parts.length != 2) {
				return null;
			}
			String patField = parts[0];
			String patAction = parts[1];

			AtomicStep s = steps.get(startIdx + k);

			if (!safeEq(s.fieldName, patField)) {
				return null;
			}
			if (!safeEq(normalize(s.actionCode), normalize(patAction))) {
				return null;
			}

			// Простейшая логика: если действие похоже на "setValue" — рассматриваем его value как аргумент метода
			if (normalize(patAction).startsWith("setvalue")) {
				args.add(s.value);
			}
		}

		Match m = new Match();
		m.methodName = methodName;
		m.length = pattern.size();
		m.args = args;
		return m;
	}

	private boolean safeEq(String a, String b) {
		if (a == null || b == null) return false;
		return a.equals(b);
	}

	private String normalize(String s) {
		return s == null ? "" : s.trim().toLowerCase();
	}
}
