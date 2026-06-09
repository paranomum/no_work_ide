package dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class BackendRequestDef {
	private String name;
	private String url;
	private String method;
	private String requestBody;
	private String requestHeaders;
	private String capturedAt;

	@Override
	public String toString() {
		return "[" + method + "] " + name + " — " + url;
	}
}