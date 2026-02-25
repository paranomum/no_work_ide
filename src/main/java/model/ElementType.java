package model;

import lombok.Getter;

/**
 * Web element types for determining corresponding custom classes
 */
@Getter
public enum ElementType {

	BUTTON("button", "Button"),
	LINKBUTTON("link-button", "Button with link"),
	TABBUTTON("tab-button", "Button with link"),
	FIELD("field", "Input field"),
	SELECT("select", "Select list"),
	DROPDOWN("dropdown", "Dropdown list"),
	CHECKBOX("checkbox-button", "Checkbox"),
	RADIO_BUTTON("radio-button", "Radio button"),
	CHECKBOX_GROUP("checkbox-group", "Checkbox Group"),
	RADIO_GROUP("radio-group", "Radio Group"),
	UNKNOWN("Unknown", "Unknown element");

	private final String className;
	private final String description;

	ElementType(String className, String description) {
		this.className = className;
		this.description = description;
	}

	public static ElementType fromClassName(String className) {
		for (ElementType type : values()) {
			if (type.className.equalsIgnoreCase(className)) {
				return type;
			}
		}
		return UNKNOWN;
	}

	@Override
	public String toString() {
		return className;
	}
}

