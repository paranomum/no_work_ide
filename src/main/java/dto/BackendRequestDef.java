package dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class BackendRequestDef {
	private String name;
	private String url;
	private String method;
	private String requestBody;
	private String requestHeaders;
	private String capturedAt;

	/**
	 * Список полей, значения которых нужно генерировать уникально при каждом запуске.
	 * Сериализуется Gson автоматически рядом с остальными полями.
	 * Если поле null (старые записи без него) — заменяем пустым списком.
	 */
	private List<DtoFieldOverride> fieldOverrides = new ArrayList<>();

	public BackendRequestDef(String name, String url, String method,
							 String requestBody, String requestHeaders, String capturedAt) {
		this.name = name;
		this.url = url;
		this.method = method;
		this.requestBody = requestBody;
		this.requestHeaders = requestHeaders;
		this.capturedAt = capturedAt;
		this.fieldOverrides = new ArrayList<>();
	}

	/** Защита от null при десериализации из старого JSON без поля fieldOverrides */
	public List<DtoFieldOverride> getFieldOverrides() {
		if (fieldOverrides == null) {
			fieldOverrides = new ArrayList<>();
		}
		return fieldOverrides;
	}

	@Override
	public String toString() {
		return "[" + method + "] " + name + " — " + url;
	}
}