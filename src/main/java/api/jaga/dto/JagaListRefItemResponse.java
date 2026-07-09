package api.jaga.dto;

import lombok.Data;

@Data
public class JagaListRefItemResponse {
	private Long id;
	private String value;
	private String color;
	private Integer orderNum;
}