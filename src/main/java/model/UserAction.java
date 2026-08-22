package model;

import lombok.Getter;

import java.util.Arrays;

@Getter
public enum UserAction {

	CLICK(ActionGroup.COMMON, "click", "Click element"),
	FILL(ActionGroup.COMMON, "fill", "Fill text field"),
	SELECT_OPTION(ActionGroup.COMMON, "selectOption", "Choose select or dropdown option"),
	SELECT_OPTIONS(ActionGroup.COMMON, "selectOptions", "Multi select options"),
	SELECT_EXACT_OPTION(ActionGroup.COMMON, "selectExactOption", "Choose select or dropdown option"),
	FILL_DATE(ActionGroup.COMMON, "fillDate", "Fill date picker"),
	CLEAR(ActionGroup.COMMON, "clear", "Clear select or fields"),

	PAUSE(ActionGroup.SPEC_ACTIONS, "pause", "pause for n seconds"),
	REFRESH_PAGE(ActionGroup.SPEC_ACTIONS, "refreshPage", "Refresh page"),
	WAIT_LOADING_PAGE(ActionGroup.SPEC_ACTIONS, "waitLoadingPage", "Wait loading of page"),
	FILL_DATA(ActionGroup.SPEC_ACTIONS, "fillData", "Filling all fields of form"),
	ASSERT_EXISTS(ActionGroup.SPEC_ACTIONS, "assertExists", "Check element exists"),
	ASSERT_NOT_EXISTS(ActionGroup.SPEC_ACTIONS, "assertNotExists", "Check element not exists"),
//	AUTH(ActionGroup.SPEC_ACTIONS, "auth", "Auth on platform with configured user"),
	SPEC_ACTION(ActionGroup.SPEC_ACTIONS, "specialAction", "Action before test"),
	SWITCH_TAB(ActionGroup.SPEC_ACTIONS, "switchTab", "Switch to tab"),
	OPEN(ActionGroup.SPEC_ACTIONS, "open", "Open URL"),
	MOVE_FULL(ActionGroup.SPEC_ACTIONS, "moveCandidateFinal", "Процессинг кандидата в массовке до финального статуса"),
	MOVE_TO_JR(ActionGroup.SPEC_ACTIONS, "moveCandidateToJr", "Процессинг кандидата в массовке до привязки к заявке"),

	CUSTOM_METHOD(ActionGroup.CUSTOM_METHOD, "customMethod", "Use ur custom method"),
	USE_BACKEND_METHOD(ActionGroup.BACKEND_METHOD, "useBackendMethod", "Use saved backend request");

	private final ActionGroup group;
	private final String code;
	private final String description;

	UserAction(ActionGroup group, String code, String description) {
		this.group = group;
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

	public static UserAction[] byGroup(ActionGroup group) {
		return Arrays.stream(values())
				.filter(a -> a.group == group)
				.toArray(UserAction[]::new);
	}

	@Override
	public String toString() {
		return code;
	}
}