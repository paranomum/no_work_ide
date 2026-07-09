package api.jaga.dto;

import lombok.Data;

import java.util.List;

@Data
public class JagaWorkflowResponse {
	private Long id;
	private String name;
	private String nameM;
	private String templateType;
	private Long projectId;
	private List<JagaWorkflowStatusResponse> statuses;
	private List<JagaWorkflowTransitionResponse> statusTransitions;
}