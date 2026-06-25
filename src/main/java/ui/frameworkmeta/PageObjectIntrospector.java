package ui.frameworkmeta;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

public class PageObjectIntrospector {

	public static List<Descriptor> scanPageClass(Class<?> pageClass) {
		List<Descriptor> result = new ArrayList<>();

		Object instance = createInstance(pageClass);
		if (instance == null) {
			return result;
		}

		for (Field f : pageClass.getDeclaredFields()) {
			Class<?> type = f.getType();
			if (!FrameworkElementTypes.isSupportedElementType(type)) {
				continue;
			}
			f.setAccessible(true);
			try {
				Object elementObj = f.get(instance);
				if (elementObj == null) {
					continue;
				}
				String label = FrameworkElementTypes.extractLabel(elementObj);

				result.add(new Descriptor(
						pageClass,
						f.getName(),
						type,
						label
				));
			} catch (IllegalAccessException e) {
				// пропускаем поле
			}
		}

		return result;
	}

	private static Object createInstance(Class<?> pageClass) {
		try {
			return pageClass.getDeclaredConstructor().newInstance();
		} catch (Exception e) {
			return null;
		}
	}

	public static class Descriptor {
		public final Class<?> pageClass;
		public final String pageSimpleName;
		public final String fieldName;
		public final Class<?> fieldType;
		public final String label;

		public Descriptor(Class<?> pageClass,
						  String fieldName,
						  Class<?> fieldType,
						  String label) {
			this.pageClass = pageClass;
			this.pageSimpleName = pageClass.getSimpleName();
			this.fieldName = fieldName;
			this.fieldType = fieldType;
			this.label = label;
		}

		@Override
		public String toString() {
			return pageSimpleName + "." + fieldName + " : "
					+ fieldType.getSimpleName() + " [" + label + "]";
		}
	}
}
