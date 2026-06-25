package dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Описывает поле, которое нужно извлечь из тела ответа backend-запроса
 * и сохранить в переменную сценария.
 * <p>
 * fieldPath   — JSON-путь до поля (например "id", "data.user.email", "items[0].id")
 * variableName — имя переменной, под которым значение будет доступно как ${variableName}
 * Дефолтное именование: requestName + "." + fieldPath
 * Отображается пользователю как json(fieldPath)
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ResponseFieldExtractor {
	private String fieldPath;
	private String variableName;
}