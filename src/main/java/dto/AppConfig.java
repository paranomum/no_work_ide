package dto;

import java.util.HashMap;
import java.util.Map;

public class AppConfig {
	public String theme = "Light";
	public String chromeDriverPath = "";
	public String openApiSpecsPath = "";
	public String usersSpecsPath = "";

	public Map<String, Integer> actionTableColumnWidths = new HashMap<>();
}

