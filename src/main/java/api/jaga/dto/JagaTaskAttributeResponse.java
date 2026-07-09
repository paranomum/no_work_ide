package api.jaga.dto;

import lombok.Data;

@Data
public class JagaTaskAttributeResponse {
	private Long id;
	private Long dictionaryId;
	private String dictionaryName;
	private Long objectTypeId;
	private String objectTypeNameM;
	private Integer orderNum;
	private Boolean required;
	private String name;
	private Boolean multiple;
	private Boolean multipleSelector;
	private Boolean visible;
	private Boolean deleted;
}