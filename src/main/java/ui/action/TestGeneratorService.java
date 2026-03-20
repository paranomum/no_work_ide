package ui.action;

import dto.ActionRecord;
import dto.Scenario;
import ui.frameworkmeta.PageObjectRegistry;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import java.util.LinkedHashMap;


public class TestGeneratorService {

	private final PageObjectRegistry registry;

	public TestGeneratorService(PageObjectRegistry registry) {
		this.registry = registry;
	}

	public void generateTestAsync(Scenario scenario) {
		new Thread(() -> generateTestInternal(scenario), "test-generator-thread").start();
	}

	private void generateTestInternal(Scenario scenario) {
		// 1. сгруппировать шаги по pageUrlPath
		Map<String, List<ActionRecord>> byPath = scenario.getActions().stream()
				.collect(Collectors.groupingBy(ActionRecord::getPageUrlPath, LinkedHashMap::new, Collectors.toList()));

		// 2. для каждого path найти PageObject-классы
		for (var entry : byPath.entrySet()) {
			String path = entry.getKey();
			List<ActionRecord> steps = entry.getValue();

			List<Class<?>> pageClasses = registry.findPageClassesForPath(path);

			// draft: если нет подходящего PageObject — генерируем как сейчас (new Field(...))
			// если есть — пробуем привязать шаги к элементам PageObject
			// и генерируем вызовы методов/геттеров
		}

		// 3. собрать итоговый Java-код и сохранить в файл
	}
}

