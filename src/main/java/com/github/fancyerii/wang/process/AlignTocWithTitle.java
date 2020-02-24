package com.github.fancyerii.wang.process;

import java.io.File;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import org.apache.commons.io.FileUtils;
import org.apache.commons.text.similarity.LevenshteinDistance;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine;
import picocli.CommandLine.Option;

@Slf4j
public class AlignTocWithTitle implements Callable<Void>{
	@Option(names = { "-m",
	"--merge-input" }, description = "合并输入， 默认值: ${DEFAULT-VALUE}", defaultValue = "/home/lili/data/wang-ocr-merge")
	private String mDir;

	
	@Option(names = { "-t",
	"--toc-input" }, description = "目录输入， 默认值: ${DEFAULT-VALUE}", defaultValue = "/home/lili/data/wang-ocr-toc-label")
	private String tDir;
	
	@Option(names = { "-o",
	"--output-dir" }, description = "输出， 默认值: ${DEFAULT-VALUE}", defaultValue = "/home/lili/data/wang-ocr-align")
	private String outDir;
	
	private Map<String,Integer> manualMap=new HashMap<>();
	
	public AlignTocWithTitle() {
		manualMap.put("蛐蛐", 254);
		manualMap.put("写字", 46);
		manualMap.put("傻子", 379);
		manualMap.put("干丝", 437);
		manualMap.put("猫", 319);
		manualMap.put("一捧雪", 383);
		manualMap.put("元宵", 60);
		manualMap.put("附录一 汪曾祺年表", 308);
		manualMap.put("附录二 汪曾祺著作目录", 322);
		
	}
	
	public static void main(String[] args) {
		CommandLine.call(new AlignTocWithTitle(), args);
	}
	
	private int pageNoToIdx(File[] mergedFiles, int pageNo) {
		for(int j=0;j<mergedFiles.length;j++) {
			String fn=mergedFiles[j].getName();
			int pn=Integer.valueOf(fn.substring(0, fn.length()-4));
			if(pn==pageNo) {
				return j;
			}
		}
		throw new RuntimeException("cant' find "+pageNo);
	}
	
	private int pageIdxToNo(File[] mergedFiles, int pageIdx) {
		String fn=mergedFiles[pageIdx].getName();
		return Integer.valueOf(fn.substring(0, fn.length()-4));
	}

	@Override
	public Void call() throws Exception {
		Gson gson=new Gson();
		new File(this.outDir).mkdirs();
		for(int i=1;i<=8;i++) {
			log.info("process book{}", i);
			File subOutDir=Paths.get(outDir, "book"+i).toFile();
			subOutDir.mkdirs();
			List<Article> articles=new ArrayList<>();
			File tocFile=Paths.get(this.tDir, "toc"+i).toFile();
			List<String> titles=FileUtils.readLines(tocFile, StandardCharsets.UTF_8);
			File subDir=Paths.get(this.mDir, "book"+i).toFile();
			File[] mergedFiles=subDir.listFiles((f)->f.getName().endsWith(".meg"));
			Arrays.sort(mergedFiles, new Comparator<File>() {
				@Override
				public int compare(File f1, File f2) {
					String fn1=f1.getName().substring(0, f1.getName().length()-4);
					String fn2=f2.getName().substring(0, f2.getName().length()-4);
					return new Integer(fn1).compareTo(new Integer(fn2));
				}
				
			});
			int j=0;
			int lastSucc=0;
			for(String title:titles) {
				Article article=new Article();
				Article lastArticle=articles.isEmpty()?null:articles.get(articles.size()-1);
				article.setTitle(title);
				articles.add(article);
				int startPage=j;
				int candidatePage=j;
				String candidateTitle="";
				double bestScore=0;
				L:
				for(;j<mergedFiles.length;j++) {

					String json=FileUtils.readFileToString(mergedFiles[j], StandardCharsets.UTF_8);
					Type listType = new TypeToken<List<BookLine>>(){}.getType();
					List<BookLine> lines=gson.fromJson(json, listType);
					for(int k=0;k<Math.min(3, lines.size());k++) {
						BookLine line=lines.get(k);
						if(line.getRect().getHeight()<35 || line.getRect().getHeight()>50) {
							double score=this.match(title, line.getText());
							if(score>bestScore) {
								bestScore=score;
								candidatePage=j;
								candidateTitle=line.getText();
							}
							if(candidateTitle.equals(title)) {
								break L;
							}
						}
					}
				}
				j=candidatePage+1;
				int lastPageNum=candidatePage-startPage;
				//log.info("{} -> {}, score: {}, lastPageNum: {}, pageIdx: {}",title, candidateTitle, bestScore, lastPageNum, candidatePage);
				if(bestScore==1.0) {
					lastSucc=candidatePage;
				}
				else if(lastPageNum>20 || bestScore < 0.3) {
					Integer label=this.manualMap.get(title);
					if(label!=null) {
						candidatePage=this.pageNoToIdx(mergedFiles, label);
						j=candidatePage+1;
						lastSucc=candidatePage;
					}else {
						log.warn("{} -> {}, score: {}, lastPageNum: {}, pageIdx: {}, page: {}",title, candidateTitle, 
								bestScore, lastPageNum, candidatePage, mergedFiles[candidatePage].getName());
						log.warn("backward from {} to {}", j, lastSucc);
						j=lastSucc;
					}
				}else {
					lastSucc=candidatePage;
				}
				article.setStartPageNo(this.pageIdxToNo(mergedFiles, candidatePage));
				if(lastArticle!=null) {
					lastArticle.setEndPageNo(article.getStartPageNo()-1);
				}
			}
			articles.get(articles.size()-1).setEndPageNo(this.pageIdxToNo(mergedFiles, mergedFiles.length-1));
			for(Article article:articles) {
				log.info("article: {}", article);
			}
			int k=0;
			for(Article article:articles) {
				k++;
				//log.info("article: {}", article);
				List<BookPage> pages=new ArrayList<>(article.getEndPageNo()-article.getStartPageNo()+1);
				article.setPages(pages);
				for(int p=article.getStartPageNo();p<=article.getEndPageNo();p++) {
					File f=Paths.get(subDir.getAbsolutePath(), p+".meg").toFile();
					String json=FileUtils.readFileToString(f, StandardCharsets.UTF_8);
					Type listType = new TypeToken<List<BookLine>>(){}.getType();
					List<BookLine> lines=gson.fromJson(json, listType);
					BookPage page=new BookPage(lines);
					pages.add(page);
				}
				String s=""+k;
				if(k<10) {
					s="0"+s;
				}
				File outFile=Paths.get(subOutDir.getAbsolutePath(), s+"_"+article.getTitle()).toFile();
				FileUtils.write(outFile, gson.toJson(article), StandardCharsets.UTF_8);
			}
		}
		return null;
	}
	
	
	private double match(String title, String s) {
		return 1.0-1.0*LevenshteinDistance.getDefaultInstance().apply(title, s)/Math.max(title.length(), s.length());
	}
}
