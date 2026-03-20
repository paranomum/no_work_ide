package ui.frameworkmeta;

import java.util.List;

// где-нибудь рядом с PageObjectRegistry или отдельным утильным классом
public class PageObjectMatcher {

	public static PageObjectIntrospector.Descriptor findMatch(
			List<PageObjectIntrospector.Descriptor> descriptors,
			String elementType,  // из таблицы (Field, Button, LinkButton...)
			String name          // Name из таблицы (лейбл)
	) {
		if (elementType == null || name == null) return null;

		String typeNorm = elementType.trim().toLowerCase();
		String nameNorm = name.trim();

		PageObjectIntrospector.Descriptor best = null;

		for (PageObjectIntrospector.Descriptor d : descriptors) {
			String dType = d.fieldType.getSimpleName().toLowerCase();
			String dLabel = d.label != null ? d.label.trim() : "";

			if (!dType.equals(typeNorm)) {
				continue;
			}

			if (!dLabel.equals(nameNorm)) {
				continue;
			}

			best = d;
			break;
		}

		return best;
	}
}

