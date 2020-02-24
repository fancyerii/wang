package com.github.fancyerii.wang;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class SearchItem {
	private String title;
	private String highlight;
	private int bookId;
	private int articleId;
	private int page;
}
