package ui.frameworkmeta;

import org.reflections.Reflections;
import org.reflections.scanners.Scanners;
import org.reflections.util.ClasspathHelper;
import org.reflections.util.ConfigurationBuilder;
import ru.rt.iqhr.framework.listeners.PageObjectUrl;

import java.net.URL;
import java.util.Set;

public class PageObjectScanner {

	private static final String BASE_PACKAGE = "ru.rt.iqhr.pageobject";

	public static Set<Class<?>> scanAllPageObjects() {
		// Берём URL'ы ровно для этого пакета и всех подпакетов
		var urls = ClasspathHelper.forPackage(BASE_PACKAGE);

		var config = new ConfigurationBuilder()
				.setUrls(urls)
				.setScanners(Scanners.TypesAnnotated)
				.filterInputsBy(path -> path != null && path.replace('/', '.').startsWith(BASE_PACKAGE));

		Reflections reflections = new Reflections(config);

		Set<Class<?>> types = reflections.getTypesAnnotatedWith(PageObjectUrl.class);

		System.out.println("[PageObjectScanner] Found @" + PageObjectUrl.class.getSimpleName()
				+ " in ru.rt.iqhr.pageobject*: " + types.size());

		for (Class<?> c : types) {
			PageObjectUrl ann = c.getAnnotation(PageObjectUrl.class);
			System.out.println("  " + c.getName() + "  " + String.join(", ", ann.value()));
		}

		return types;
	}
}
