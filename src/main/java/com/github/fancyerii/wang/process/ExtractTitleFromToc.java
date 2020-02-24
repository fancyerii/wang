package com.github.fancyerii.wang.process;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

import org.apache.commons.io.FileUtils;
 
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine;
import picocli.CommandLine.Option;

@Slf4j
public class ExtractTitleFromToc implements Callable<Void>{
	@Option(names = { "-i",
	"--in-dir" }, description = "原始识别结果， 默认值: ${DEFAULT-VALUE}", defaultValue = "/home/lili/data/wang-ocr")
	private String inDir;

	
	@Option(names = { "-o",
	"--out-dir" }, description = "输出目录， 默认值: ${DEFAULT-VALUE}", defaultValue = "/home/lili/data/wang-ocr-toc")
	private String outDir;
	
	private int[] tocStart=new int[] {22, 11, 17, 6, 6, 7, 18, 18};
	private int[] tocEnd=new int[] {23, 14, 20, 9, 9, 10, 18, 19};//包含
	public static void main(String[] args) {
		CommandLine.call(new ExtractTitleFromToc(), args);
	}

	@Override
	public Void call() throws Exception {
		JsonParser parser=new JsonParser();
		new File(this.outDir).mkdirs();
		for(int i=1;i<=8;i++) {
			log.info("process book{}", i);
			int startPage=tocStart[i-1];
			int endPage=tocEnd[i-1];
			List<String> titles=new ArrayList<>();
			for(int j=startPage;j<=endPage;j++) {
				File file=Paths.get(this.inDir, "book"+i, j+".txt").toFile();
				String json=FileUtils.readFileToString(file, StandardCharsets.UTF_8);
				JsonObject txtJson=parser.parse(json).getAsJsonObject();
				JsonArray ja=txtJson.get("words_result").getAsJsonArray();
				List<String> lines=new ArrayList<>();
				for(int k=0;k<ja.size();k++) {
					JsonObject jo=ja.get(k).getAsJsonObject();
					lines.add(jo.get("words").getAsString()); 
				}
				
				for(String line:lines) {
					if(StringTool.hasChinese(line) && !line.contains("目录") && !line.contains("全集")) {
						line=line.replaceAll("…", "");
						line=line.replaceAll("\\(\\d+\\)", "");
						line=line.trim();
						titles.add(line);
						log.info("    {}", line);
					}
				}

			}
			File outFile=Paths.get(this.outDir, "toc"+i).toFile();
			FileUtils.write(outFile, String.join("\n", titles), StandardCharsets.UTF_8);
		}
		return null;
	}

}
