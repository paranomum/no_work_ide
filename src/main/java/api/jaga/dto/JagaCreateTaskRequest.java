package api.jaga.dto;

import lombok.Data;
import java.util.List;

@Data
public class JagaCreateTaskRequest {
	private Integer orderNum;
	private Long statusId;
	private Integer statusModifier;
	private List<JagaCreateTaskAttributeRequest> attributes;
	private List<Long> attachmentIds;
}