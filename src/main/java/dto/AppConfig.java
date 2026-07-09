package dto;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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

	// НОВОЕ: список доменных имён
	public List<String> domains = new ArrayList<>();
	// НОВОЕ: последний выбранный домен
	public String selectedDomain = "";

	public Map<String, Integer> actionTableColumnWidths = new HashMap<>();

	// новое
	public String jagaUserSettingsPath = "";
	public JagaUserSettings jagaUserSettings = new JagaUserSettings();
}