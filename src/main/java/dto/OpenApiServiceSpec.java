package dto;

public class OpenApiServiceSpec {
	public String service;
	public String spec;

	public OpenApiServiceSpec() {}

	public OpenApiServiceSpec(String service, String spec) {
		this.service = service;
		this.spec = spec;
	}
}
