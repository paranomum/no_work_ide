package dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Одно уникальное поле в теле backend-запроса.
 *
 * fieldPath  — JSON-ключ поля (например "email", "phone", "user.email")
 * method     — метод генерации: addUuid | generateEmail | generatePhoneNumber
 * methodArg  — аргумент для addUuid (префикс); для остальных методов игнорируется
 * unique     — включена ли уникализация
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DtoFieldOverride {
	private String fieldPath;
	private String method;
	private String methodArg;
	private boolean unique;
}