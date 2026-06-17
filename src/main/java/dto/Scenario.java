package dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Scenario {
	private List<ActionRecord> actions;
	private List<LocalVariables> variables;

	/**
	 * Backend-запросы, используемые в сценарии.
	 * Сохраняются только те запросы, которые присутствуют в actions как useBackendMethod.
	 * Позволяет передать сценарий другому человеку без ручного экспорта backend_requests.json.
	 * null/пустой список — обратная совместимость со старыми сценариями.
	 */
	private List<BackendRequestDef> backendRequests;

	/**
	 * Сценарные overrides для fieldOverrides и responseExtractors (Пункт 6).
	 * Ключ — имя backend-запроса.
	 * Значение — настройки, специфичные для данного сценария.
	 */
	private Map<String, ScenarioBackendConfig> scenarioOverrides;
}