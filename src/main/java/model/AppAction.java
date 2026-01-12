package model;

import lombok.Getter;

/**
 * Application actions
 * What IDE does: pause recording, save to variable, etc.
 */
@Getter
public enum AppAction {

	SAVE_VARIABLE("saveVar", "Save text to variable"),
	PAUSE("pause", "Pause recording");

	private final String code;
	private final String description;

	AppAction(String code, String description) {
		this.code = code;
		this.description = description;
	}

	public static AppAction fromCode(String code) {
		for (AppAction action : values()) {
			if (action.code.equals(code)) {
				return action;
			}
		}
		throw new IllegalArgumentException("Unknown app action: " + code);
	}

	@Override
	public String toString() {
		return code;
	}
}
