package api.jaga.dto;

import lombok.Data;
import java.util.List;

@Data
public class JagaTaskTypeGroupResponse {
	private Long id;
	private String title;
	private Integer orderNum;
	private Boolean isUngrouped;
	private Boolean deleted;
	private List<JagaTaskAttributeResponse> attributes;
}