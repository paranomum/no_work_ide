package api.jaga.dto;

import lombok.Data;

import java.util.List;

@Data
public class SearchResultDto {
	private List<SearchItems> content;
	private Integer totalPages;
	private Integer pageNumber;
	private Long totalElements;
}