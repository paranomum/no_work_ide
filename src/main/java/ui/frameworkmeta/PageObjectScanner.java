package ui.frameworkmeta;

import org.reflections.Reflections;
import org.reflections.util.ClasspathHelper;
import org.reflections.util.ConfigurationBuilder;
import ru.rt.iqhr.framework.listeners.PageObjectUrl;

import java.util.Set;

import static org.reflections.scanners.Scanners.TypesAnnotated;

public class PageObjectScanner {

	private static final String BASE_PACKAGE = "ru.rt.iqhr.pageobject";

	public static Set<Class<?>> scanAllPageObjects() {
		var urls = ClasspathHelper.forPackage(BASE_PACKAGE);

		var config = new ConfigurationBuilder()
				.setUrls(urls)
				.setScanners(TypesAnnotated)
				.filterInputsBy(path ->
						path != null && path.replace('/', '.').startsWith(BASE_PACKAGE));

		Reflections reflections = new Reflections(config);

		// ВАЖНО: используем новый API, но результат тот же Set<Class<?>>
		Set<Class<?>> types = reflections
				.get(TypesAnnotated.with(PageObjectUrl.class).asClass());

		System.out.println("[PageObjectScanner] Found @" + PageObjectUrl.class.getSimpleName()
				+ " in " + BASE_PACKAGE + "*: " + types.size());

		for (Class<?> c : types) {
			PageObjectUrl ann = c.getAnnotation(PageObjectUrl.class);
			System.out.println("  " + c.getName() + "  " + String.join(", ", ann.value()));
		}

		return types;
	}
}
