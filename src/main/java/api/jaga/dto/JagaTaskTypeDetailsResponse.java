package api.jaga.dto;

import lombok.Data;
import java.util.List;

@Data
public class JagaTaskTypeDetailsResponse {
	private Long id;
	private Long projectId;
	private String typeName;
	private List<JagaTaskTypeGroupResponse> groups;
	private Long workflowId;
}