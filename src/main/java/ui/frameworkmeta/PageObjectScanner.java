package ui.frameworkmeta;

import org.reflections.Reflections;
import org.reflections.scanners.Scanners;
import ru.rt.iqhr.framework.listeners.PageObjectUrl;

import java.util.Set;

public class PageObjectScanner {

	private static final String PAGEOBJECT_PACKAGE = "ru.rt.iqhr.pageobject";

	public static Set<Class<?>> scanAllPageObjects() {
		Reflections reflections = new Reflections(PAGEOBJECT_PACKAGE,
				Scanners.TypesAnnotated);

		return reflections.getTypesAnnotatedWith(PageObjectUrl.class);
	}
}
