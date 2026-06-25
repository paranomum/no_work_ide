package model;

public enum ActionGroup {
	COMMON("common"),
	SPEC_ACTIONS("spec_actions"),
	CUSTOM_METHOD("custom_method"),
	BACKEND_METHOD("backend_method");

	private final String code;

	ActionGroup(String code) {
		this.code = code;
	}

	public String getCode() {
		return code;
	}

	@Override
	public String toString() {
		return code;
	}
}