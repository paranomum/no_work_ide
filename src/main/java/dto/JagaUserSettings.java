package dto;

import lombok.Getter;
import lombok.Setter;

import java.util.LinkedHashMap;
import java.util.Map;

@Getter
@Setter
public class JagaUserSettings {
	private String email = "";
	private Long projectId;
	private Map<Long, String> taskTypes = new LinkedHashMap<>();

	private String encryptedPassword = "";

	private transient String password = "";
}