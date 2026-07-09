package api.jaga.dto;

import lombok.Data;

@Data
public class JagaWorkflowTransitionResponse {
	private Long id;
	private String name;
	private String nameM;
	private Long statusFromId;
	private Long statusToId;
	private Integer transitionMod;
	private Long scriptId;
	private String viewData;
	private Long constraintFormId;
	private Long approvalId;
	private Long approvalWithDeclineId;
}