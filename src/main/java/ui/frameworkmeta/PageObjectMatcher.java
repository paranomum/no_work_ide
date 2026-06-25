package ui.frameworkmeta;

import util.XPathSimilarity;

import java.util.List;

// где-нибудь рядом с PageObjectRegistry или отдельным утильным классом
public class PageObjectMatcher {

	public static PageObjectIntrospector.Descriptor findMatch(
			List<PageObjectIntrospector.Descriptor> descriptors,
			String elementType,
			String name,
			String recordedXpath
	) {
		if (elementType == null || name == null) return null;

		String typeNorm = elementType.trim().toLowerCase();
		String nameNorm = name.trim();

		System.out.println("[Matcher] start: type=" + elementType + ", name=" + name
				+ ", xpath=" + recordedXpath);

		PageObjectIntrospector.Descriptor bestExact = null;
		PageObjectIntrospector.Descriptor bestWithXpath = null;

		for (PageObjectIntrospector.Descriptor d : descriptors) {
			String dType = d.fieldType.getSimpleName().toLowerCase();
			String dLabel = d.label != null ? d.label.trim() : "";

			System.out.println("  [Matcher] candidate: "
					+ d.pageSimpleName + "." + d.fieldName
					+ " type=" + dType + ", label=" + dLabel);

			if (!dType.equals(typeNorm)) {
				System.out.println("    -> skip: type mismatch");
				continue;
			}
			if (!dLabel.equals(nameNorm)) {
				System.out.println("    -> skip: label mismatch");
				continue;
			}

			System.out.println("    -> type+label matched");

			// если нет xpath, считаем хороший матч
			if (recordedXpath == null || recordedXpath.isBlank()) {
				bestExact = d;
				System.out.println("    -> no recorded xpath, accept as bestExact");
				break;
			}

			String poXpath = tryGetElementXpath(d);
			System.out.println("    -> poXpath=" + poXpath);

			if (poXpath != null) {
				boolean same = XPathSimilarity.isProbablySame(recordedXpath, poXpath);
				System.out.println("    -> xpath similarity = " + same);
				if (same) {
					bestWithXpath = d;
					System.out.println("    -> accept as bestWithXpath");
					break;
				}
			}

			if (bestExact == null) {
				bestExact = d;
				System.out.println("    -> remember as bestExact (no xpath match)");
			}
		}

		if (bestWithXpath != null) {
			System.out.println("[Matcher] RESULT bestWithXpath: "
					+ bestWithXpath.pageSimpleName + "." + bestWithXpath.fieldName);
			return bestWithXpath;
		}
		if (bestExact != null) {
			System.out.println("[Matcher] RESULT bestExact: "
					+ bestExact.pageSimpleName + "." + bestExact.fieldName);
			return bestExact;
		}

		System.out.println("[Matcher] RESULT: null");
		return null;
	}

	private static String tryGetElementXpath(PageObjectIntrospector.Descriptor d) {
		try {
			var field = d.pageClass.getDeclaredField(d.fieldName);
			field.setAccessible(true);
			Object instance = d.pageClass.getDeclaredConstructor().newInstance();
			Object el = field.get(instance);
			if (el == null) return null;

			for (String methodName : new String[]{"getXpath", "getLocator", "getBy"}) {
				try {
					var m = el.getClass().getMethod(methodName);
					Object v = m.invoke(el);
					if (v != null) {
						return v.toString();
					}
				} catch (NoSuchMethodException ignore) {
				}
			}
		} catch (Exception e) {
			System.out.println("    -> tryGetElementXpath error: " + e.getMessage());
		}
		return null;
	}
}
