package api.jaga.dto;

import lombok.Data;

import java.time.OffsetDateTime;
import java.util.List;

@Data
public class ItemAttribute {
	private Long id;
	private String typeName;
	private OffsetDateTime closeTs;
	private String iconName;
	private String iconColor;
	private Boolean hasScript;
	private OffsetDateTime createTs;
	private OffsetDateTime updateTs;
	private String author;
	private Long ownerProjectId;
	private Long migrId;
	private String migrSrc;
	private List<String> modulesEnabled;
}