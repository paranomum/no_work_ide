package model;

import lombok.Getter;

/**
 * Action types that can be recorded by IDE for test generation
 */
@Getter
public enum VariableAction {

	ADD_UUID("addUuid", "add Uuid to string"),
	GENERATE_EMAIL("generateEmail", "Fill text field"),
	GENERATE_PHONE_NUMBER("generatePhoneNumber", "Choose select or dropdown option");

	private final String code;
	private final String description;

	VariableAction(String code, String description) {
		this.code = code;
		this.description = description;
	}

	public static VariableAction fromCode(String code) {
		for (VariableAction action : values()) {
			if (action.code.equals(code)) {
				return action;
			}
		}
		throw new IllegalArgumentException("Unknown user action: " + code);
	}

	@Override
	public String toString() {
		return code;
	}
}
