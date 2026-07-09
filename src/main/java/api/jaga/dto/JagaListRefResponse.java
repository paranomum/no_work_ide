package api.jaga.dto;

import lombok.Data;
import java.util.List;

@Data
public class JagaListRefResponse {
	private Long id;
	private Long templateId;
	private String creationDate;
	private String editionDate;
	private String name;
	private String listNameM;
	private Boolean isSystem;
	private List<JagaListRefItemResponse> items;
}