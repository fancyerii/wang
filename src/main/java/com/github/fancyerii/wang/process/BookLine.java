package com.github.fancyerii.wang.process;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class BookLine {
	private String text;
	private Rect rect;
}
