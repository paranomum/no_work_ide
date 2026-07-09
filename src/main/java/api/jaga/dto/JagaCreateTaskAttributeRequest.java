package api.jaga.dto;

import lombok.Data;

@Data
public class JagaCreateTaskAttributeRequest {
	private Long fieldId;
	private Object value;
	private String objectTypeNameM;
	private Boolean referenceValue;
	private Long dictionaryId;
	private String mnemo;
}