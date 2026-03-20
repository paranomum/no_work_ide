package ui.frameworkmeta;

import ru.rt.iqhr.framework.listeners.PageObjectUrl;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class PageObjectRegistry {

	private final List<Class<?>> pageObjectClasses;

	public PageObjectRegistry() {
		Set<Class<?>> scanned = PageObjectScanner.scanAllPageObjects();
		this.pageObjectClasses = new ArrayList<>(scanned);

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

	public List<PageObjectIntrospector.Descriptor> getAllPageObjectDescriptors() {
		List<PageObjectIntrospector.Descriptor> result = new ArrayList<>();
		for (Class<?> cls : pageObjectClasses) {
			result.addAll(PageObjectIntrospector.scanPageClass(cls));
		}
		return result;
	}
}