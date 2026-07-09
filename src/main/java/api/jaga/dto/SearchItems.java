package api.jaga.dto;

import lombok.Data;

@Data
public class SearchItems {
	private Long id;
	private String code;
	private String title;
	private ItemAttribute typeRef;
}