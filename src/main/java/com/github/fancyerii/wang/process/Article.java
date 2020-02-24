package com.github.fancyerii.wang.process;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Article {
	private String title;
	private List<BookPage> pages;
	private int startPageNo;
	private int endPageNo; //inclusive
}
