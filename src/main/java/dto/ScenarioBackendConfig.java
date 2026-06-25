package dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * Сценарные настройки для конкретного backend-запроса.
 * Хранится в Scenario.scenarioOverrides[requestName].
 * Переопределяет глобальные fieldOverrides и responseExtractors из BackendRequestDef.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ScenarioBackendConfig {
	/**
	 * Переопределяет BackendRequestDef.fieldOverrides для данного сценария
	 */
	private List<DtoFieldOverride> fieldOverrides;
	/**
	 * Переопределяет BackendRequestDef.responseExtractors для данного сценария
	 */
	private List<ResponseFieldExtractor> responseExtractors;
}