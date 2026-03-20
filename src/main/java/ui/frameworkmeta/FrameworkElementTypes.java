package ui.frameworkmeta;

import org.reflections.Reflections;
import org.reflections.scanners.Scanners;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;

public class FrameworkElementTypes {

	private static final String ELEMENTS_PACKAGE = "ru.rt.iqhr.framework.pageobject";

	private static final Set<Class<?>> ELEMENT_TYPES = new HashSet<>();

	static {
		// сканим только нужный пакет и детей
		Reflections reflections = new Reflections(ELEMENTS_PACKAGE, Scanners.SubTypes.filterResultsBy(s -> true));

		// берём все классы в этом дереве пакетов
		Set<Class<?>> allTypes = reflections.getSubTypesOf(Object.class);

		for (Class<?> type : allTypes) {
			if (isElementType(type)) {
				ELEMENT_TYPES.add(type);
			}
		}

		System.out.println("[FrameworkElementTypes] Detected element types:");
		for (Class<?> t : ELEMENT_TYPES) {
			System.out.println("  " + t.getName());
		}
	}

	private static boolean isElementType(Class<?> type) {
		// отсеиваем абстрактные и внутренние системные
		if (type.isInterface() || type.isAnnotation() || type.isEnum()) {
			return false;
		}
		if (type.getName().contains("$")) {
			return false;
		}

		// критерий «похожести» на web-element:
		// есть публичный метод getTitle() или getName()
		for (Method m : type.getMethods()) {
			if ((m.getName().equals("getTitle") || m.getName().equals("getName"))
					&& m.getParameterCount() == 0
					&& m.getReturnType() == String.class) {
				return true;
			}
		}
		return false;
	}

	public static boolean isSupportedElementType(Class<?> type) {
		return ELEMENT_TYPES.stream().anyMatch(t -> t.isAssignableFrom(type));
	}

	public static String extractLabel(Object elementObj) {
		if (elementObj == null) return "";

		try {
			Method m = elementObj.getClass().getMethod("getTitle");
			Object v = m.invoke(elementObj);
			return v != null ? v.toString() : "";
		} catch (Exception ignore) {
		}
		try {
			Method m = elementObj.getClass().getMethod("getName");
			Object v = m.invoke(elementObj);
			return v != null ? v.toString() : "";
		} catch (Exception ignore) {
		}
		return "";
	}
}
