package com.github.fancyerii.wang.process;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.StringEscapeUtils;
import org.apache.commons.text.similarity.LevenshteinDistance;
 
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine;
import picocli.CommandLine.Option;

@Slf4j
public class MergeBaidResult implements Callable<Void>{
	@Option(names = { "-i",
	"--in-dir" }, description = "输入目录， 默认值: ${DEFAULT-VALUE}", defaultValue = "/home/lili/data/wang-ocr")
	private String inDir;
	
	@Option(names = { "-o",
	"--out-dir" }, description = "输出目录， 默认值: ${DEFAULT-VALUE}", defaultValue = "/home/lili/data/wang-ocr-merge")
	private String outDir;
	
	//去掉目录之前的
	private int[] bookStarts=new int[] {24, 15, 21, 10, 10, 11, 19, 20};
	
	public static void main(String[] args) {
		CommandLine.call(new MergeBaidResult(), args);
	}
	
	private List<BookLine> mergeLines(List<BookLine> lines){
		List<BookLine> result=new ArrayList<>();
		for(int i=0;i<lines.size();) {
			BookLine line=lines.get(i);
			int j=i+1;
			for(;j<lines.size();j++) {
				BookLine nextLine=lines.get(j);
				BookLine merged=this.mergeLine(line, nextLine);
				if(merged==null) break;
				//log.info("merge {}, {} -> {}", line.getRect(), nextLine.getRect(), merged.getRect());
				line=merged;
			}
			result.add(line);
			i=j;
		}
		
		return result;
	}
	
	private boolean contains(Rect rect1, Rect rect2) {
		return rect1.getTop() < rect2.getTop() && rect1.getTop()+rect1.getHeight() > rect2.getTop()+rect2.getHeight();	
	}
	
	private BookLine mergeLine(BookLine line1, BookLine line2) {
		Rect rect1=line1.getRect();
		Rect rect2=line2.getRect();
		int topDiff=Math.abs(rect1.getTop()-rect2.getTop());
		int leftRightMatch=rect1.getLeft()+rect1.getWidth()-rect2.getLeft();
		boolean contains1=this.contains(rect1, rect2);
		boolean contains2=this.contains(rect2, rect1);
		if((topDiff<20 && leftRightMatch < 50 && leftRightMatch > -200) ||
				(contains1 || contains2)) {
			int newTop=Math.min(rect1.getTop(), rect2.getTop());
			int newBottom=Math.max(rect1.getTop()+rect1.getHeight(), rect2.getTop()+rect2.getHeight());
			int newHeight=newBottom-newTop;
			int newLeft=rect1.getLeft();
			int newRight=rect2.getLeft()+rect2.getWidth();
			
			
			return new BookLine(line1.getText()+line2.getText(), new Rect(newTop, newLeft, newRight-newLeft, newHeight));
		}else {
			return null;
		}
	}
	
	private String normText(String s) {
		s=StringEscapeUtils.unescapeJava(s);
		StringBuilder sb=new StringBuilder("");
		for(int i=0;i<s.length();i++) {
			if(StringTool.isCnEnDigit(s.charAt(i))) {
				sb.append(s.charAt(i));
			}
		}
		return sb.toString();
	}

	@Override
	public Void call() throws Exception {
		JsonParser parser=new JsonParser();
		Gson gson=new Gson();
		File[] books=new File(inDir).listFiles();
		File dir=new File(this.outDir);
		dir.mkdirs();
		for(File book:books) {
			File subDir=Paths.get(dir.getAbsolutePath(), book.getName()).toFile();
			subDir.mkdirs();
			int bNum=Integer.valueOf(book.getName().substring(4))-1;
			File[] pages=book.listFiles(f->f.getName().endsWith(".txt"));
			
			for(File page:pages) {
				String name=page.getName();
				int p=Integer.valueOf(name.substring(0, name.length()-4));
				if(p<this.bookStarts[bNum]) {
					log.info("skip: {}/{}", book.getName(), page.getName());
					continue;
				}
				log.info("process: {}/{}",book.getName(),page.getName());
				if(book.getName().equals("book2") && page.getName().equals("210.txt")) {
					System.out.println("here");
				}
				String txt=FileUtils.readFileToString(page, StandardCharsets.UTF_8);
				Path posFile=Paths.get(book.getAbsolutePath(), page.getName().replace(".txt", ".pos"));
				String pos=FileUtils.readFileToString(posFile.toFile(), StandardCharsets.UTF_8);
				JsonObject txtJson=parser.parse(txt).getAsJsonObject();
				JsonObject posJson=parser.parse(pos).getAsJsonObject();
				
				JsonArray ja=txtJson.get("words_result").getAsJsonArray();
				List<String> lines=new ArrayList<>();
				for(int i=0;i<ja.size();i++) {
					JsonObject jo=ja.get(i).getAsJsonObject();
					lines.add(jo.get("words").getAsString()); 
				}
				 
				List<BookLine> bookLines=new ArrayList<>();
				JsonArray ja2=posJson.get("words_result").getAsJsonArray();
				for(int i=0;i<ja2.size();i++) {
					JsonObject jo=ja2.get(i).getAsJsonObject();
					String line=jo.get("words").getAsString();
					JsonObject loc=jo.get("location").getAsJsonObject();
					Rect rect=new Rect(loc.get("top").getAsInt(), loc.get("left").getAsInt(), 
							loc.get("width").getAsInt(), loc.get("height").getAsInt());
					bookLines.add(new BookLine(line, rect));
				}
				bookLines=this.mergeLines(bookLines);
				if(lines.size()!=bookLines.size()) {
					int[] align1=new int[lines.size()];
					int[] align2=new int[bookLines.size()];
					Arrays.fill(align1, -1);
					Arrays.fill(align2, -1);
					for(int i=0;i<align2.length;i++) {
						String s1=bookLines.get(i).getText();
						s1=this.normText(s1);
						for(int j=0;j<align1.length;j++) {
							if(align1[j]!=-1) continue;
							
							String s2=lines.get(j);
							s2=this.normText(s2);
							int dist=LevenshteinDistance.getDefaultInstance().apply(s1, s2);
							if(dist*1.0/Math.max(s1.length(), s2.length())<0.2) {
								align2[i]=j;
								align1[j]=i;
								String newText=StringEscapeUtils.unescapeJava(lines.get(j));
								bookLines.get(i).setText(newText);
								break;
							}
						}
						
						
					}
//					StringBuilder sb=new StringBuilder("");
//					for(int k=0;k<align1.length;k++) {
//						if(align1[k]==-1) {
//							sb.append(k).append(" ");
//						}
//					}
//					String s=sb.toString().trim();
//					if(!s.isEmpty()) {
//						log.warn("miss1: "+s);
//					}
//					sb.setLength(0);
//					for(int k=0;k<align2.length;k++) {
//						if(align2[k]==-1) {
//							sb.append(k).append(" ");
//						}
//					}
//					s=sb.toString().trim();
//					if(!s.isEmpty()) {
//						log.warn("miss2: "+s);
//					}
				}
				else {
					for(int i=0;i<lines.size();i++) {
						String s1=lines.get(i);
						String s2=bookLines.get(i).getText();
						s1=this.normText(s1);
						s2=this.normText(s2);
						int dist=LevenshteinDistance.getDefaultInstance().apply(s1, s2);
						if(dist*1.0/Math.max(s1.length(), s2.length())>0.5 || Math.abs(s1.length()-s2.length())>2) {
							//log.warn("mismatch: {}, {}", s1, s2);
						}else {
							String newText=StringEscapeUtils.unescapeJava(lines.get(i));
							bookLines.get(i).setText(newText);
						}
						
					}
//					for(int i=0;i<lines.size();i++) {
//						String line1=lines.get(i);
//						String line2=lines2.get(i);
//						if(!line1.equals(line2)) {
//							//log.warn("{}\t{}", line1, line2);
//						}
//					}
				}
				File outFile=Paths.get(subDir.getAbsolutePath(), p+".meg").toFile();
				FileUtils.writeStringToFile(outFile, gson.toJson(bookLines), StandardCharsets.UTF_8);
			}
		}
		return null;
	}

}
