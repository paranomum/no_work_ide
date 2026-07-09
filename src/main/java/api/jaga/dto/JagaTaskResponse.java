package api.jaga.dto;

import lombok.Data;
import java.util.List;

@Data
public class JagaTaskResponse {
	private Long id;
	private Long orderNum;
	private Long statusId;
	private Long statusModifierId;
	private Long taskTypeId;
	private String code;
	private String updateTs;
	private List<JagaTaskCreationAttributeResponse> attributes;
	private List<Long> statusTransitions;
}