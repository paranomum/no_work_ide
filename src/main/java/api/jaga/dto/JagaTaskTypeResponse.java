package api.jaga.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class JagaTaskTypeResponse {
	private Long id;
	private String typeName;
	private String closeTs;
	private String iconName;
	private String iconColor;
	private Boolean hasScript;
	private String createTs;
	private String updateTs;
	private String author;
	private Long ownerProjectId;
	private Long migrId;
	private String migrSrc;
	private List<String> modulesEnabled;
}