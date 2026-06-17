package dto;

import java.util.HashMap;
import java.util.Map;

public class AppConfig {
	public String theme = "Light";
	public String chromeDriverPath = "";
	public String openApiSpecsPath = "";
	public String customMethodsPath = "";
	public String usersSpecsPath = "";
	public String backendRequestsPath = "";

	public String trustStorePath = "";
	public String trustStorePassword = "changeit";
	public String trustStoreType = "PKCS12";

	public Map<String, Integer> actionTableColumnWidths = new HashMap<>();
}

