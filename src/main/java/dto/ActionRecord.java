package dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Model для строки записи действия.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ActionRecord {
	private String action;
	private String selector;
	private String value;
	private String comment;
	private String elementType;
}

