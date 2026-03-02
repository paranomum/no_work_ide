package model;

import lombok.Getter;

/**
 * Action types that can be recorded by IDE for test generation
 */
@Getter
public enum UserAction {

	CLICK("click", "Click element"),
	FILL("fill", "Fill text field"),
	SELECT_OPTION("selectOption", "Choose select or dropdown option"),
	SELECT_EXACT_OPTION("selectExactOption", "Choose select or dropdown option"),
	FILL_DATE("fillDate", "Fill date picker"),
	WAIT_LOADING_PAGE("waitLoadingPage", "Wait loading of page"),
	FILL_DATA("fillData", "Filling all fields of form"),
	SPEC_ACTION("specialAction", "Action before test"),
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
