package api.jaga.dto;

import lombok.Data;

@Data
public class JagaTaskCreationAttributeResponse {
	private Long fieldId;
	private Object value;
	private Object entityValue;
	private String objectTypeNameM;
	private Long dictionaryId;
	private String mnemo;
	private Boolean visible;
	private Boolean referenceValue;
}
