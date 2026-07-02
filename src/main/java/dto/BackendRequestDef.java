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
	private String capturedResponseBody;
	private List<ResponseFieldExtractor> responseExtractors = new ArrayList<>();
	private List<DtoFieldOverride> fieldOverrides = new ArrayList<>();
	private String token;
	private String bodyType = "JSON";
	private List<FormDataParam> formData = new ArrayList<>();

	public BackendRequestDef(String name, String url, String method,
							 String requestBody, String requestHeaders, String capturedAt) {
		this.name = name;
		this.url = url;
		this.method = method;
		this.requestBody = requestBody;
		this.requestHeaders = requestHeaders;
		this.capturedAt = capturedAt;
		this.fieldOverrides = new ArrayList<>();
		this.formData = new ArrayList<>();
	}

	public List<DtoFieldOverride> getFieldOverrides() {
		if (fieldOverrides == null) {
			fieldOverrides = new ArrayList<>();
		}
		return fieldOverrides;
	}

	public List<ResponseFieldExtractor> getResponseExtractors() {
		if (responseExtractors == null) {
			responseExtractors = new ArrayList<>();
		}
		return responseExtractors;
	}

	public List<FormDataParam> getFormData() {
		if (formData == null) {
			formData = new ArrayList<>();
		}
		return formData;
	}

	public String getBodyType() {
		if (bodyType == null || bodyType.isBlank()) {
			bodyType = "JSON";
		}
		return bodyType;
	}

	@Override
	public String toString() {
		return "[" + method + "] " + name + " — " + url;
	}
}