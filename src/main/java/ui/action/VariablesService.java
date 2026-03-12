package ui.action;

import dto.LocalVariables;

import java.util.*;

public class VariablesService {

	private final Map<String, LocalVariables> variables = new HashMap<>();

	public void addVariable(String name, String value, String method) {
		variables.put(name, new LocalVariables(name, value, method));
	}

	public void addVariable(String name, String value) {
		String method = null;
		if (value.contains("generateEmail") || value.contains("generatePhoneNumber") || value.contains("addUuid")) {
			String[] split = value.split("\\(");
			method = split[0];
			value = split[1].replace(")", "");
		}
		variables.put(name, new LocalVariables(name, value, method));
	}

	// перегрузка, если уже есть готовый объект
	public void addVariable(LocalVariables variable) {
		variables.put(variable.getName(), variable);
	}

	// получить текущий список переменных (read‑only)
	public List<LocalVariables> getVariables() {
		List<LocalVariables> local = new ArrayList<>();
		for (String key : variables.keySet()) {
			local.add(variables.get(key));
		}
		return Collections.unmodifiableList(local);
	}

	public List<String> getVariableNames() {
		return variables.keySet().stream().toList();
	}

	public String getVariableValueByNameFormatted(String variable) {
		LocalVariables var = variables.get(variable);
		if (var.getMethod() != null && !var.getMethod().equals("addUuid"))
			return var.getMethod() + "()";
		else if (var.getMethod() != null)
			return "addUuid(" + var.getValue() + ")";
		else
			return var.getValue();
	}

	public String getVariableValueByName(String variable) {
		LocalVariables var = variables.get(variable.substring(2, variable.length() - 1));
		if (var.getMethod() != null && !var.getMethod().equals("addUuid"))
			return var.getMethod() + "()";
		else if (var.getMethod() != null)
			return "addUuid(" + var.getValue() + ")";
		else
			return var.getValue();
	}

	public void clear() {
		variables.clear();
	}
}
