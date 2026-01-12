package model;

import lombok.Getter;

/**
 * Action types that can be recorded by IDE for test generation
 */
@Getter
public enum UserAction {

	CLICK("click", "Click element"),
	DOUBLE_CLICK("doubleClick", "Double click element"),
	FILL("fill", "Fill text field"),
	SELECT("select", "Select dropdown option"),
	SWITCH_TAB("switchTab", "Switch to tab"),
	OPEN("open", "Open URL");

	private final String code;
	private final String description;

	UserAction(String code, String description) {
		this.code = code;
		this.description = description;
	}

	public static UserAction fromCode(String code) {
		for (UserAction action : values()) {
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
