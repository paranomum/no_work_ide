package ui.frameworkmeta;

import ru.rt.iqhr.framework.listeners.PageObjectUrl;
import ru.rt.iqhr.pageobject.angular.pages.AuthorizationPage;
import ru.rt.iqhr.pageobject.react.users.User;
import ru.rt.iqhr.pageobject.react.users.UsersPage;

import java.util.ArrayList;
import java.util.List;

public class PageObjectRegistry {

	// DRAFT: руками перечисляем, что есть
	private final List<Class<?>> pageObjectClasses = List.of(
			AuthorizationPage.class,
			UsersPage.class,
			User.class
	);

	public PageObjectRegistry() {
		System.out.println("[PageObjectRegistry] Detected page objects:");
		for (Class<?> c : pageObjectClasses) {
			PageObjectUrl ann = c.getAnnotation(PageObjectUrl.class);
			System.out.println("  " + c.getName() + "  " + String.join(", ", ann.value()));
		}
	}

	public List<Class<?>> findPageClassesForPath(String pageUrlPath) {
		List<Class<?>> result = new ArrayList<>();
		if (pageUrlPath == null) return result;

		for (Class<?> cls : pageObjectClasses) {
			PageObjectUrl ann = cls.getAnnotation(PageObjectUrl.class);
			if (ann == null) continue;
			for (String pattern : ann.value()) {
				if (pattern != null && !pattern.isBlank()
						&& pageUrlPath.contains(pattern)) {
					result.add(cls);
					break;
				}
			}
		}
		return result;
	}

	public List<PageObjectIntrospector.Descriptor> getElementsForPath(String pageUrlPath) {
		List<PageObjectIntrospector.Descriptor> result = new ArrayList<>();
		for (Class<?> cls : findPageClassesForPath(pageUrlPath)) {
			result.addAll(PageObjectIntrospector.scanPageClass(cls));
		}
		return result;
	}
}
