package com.github.fancyerii.wang.index;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.Callable;

import org.apache.commons.io.FileUtils;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.core.WhitespaceAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.IndexWriterConfig.OpenMode;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;


import com.github.fancyerii.wang.tool.Seg;
import com.github.fancyerii.wang.process.Article;
import com.github.fancyerii.wang.process.BookLine;
import com.github.fancyerii.wang.process.BookPage;
import com.google.gson.Gson;

import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine;
import picocli.CommandLine.Option;

@Slf4j
public class Main implements Callable<Void> {
	@Option(names = { "-d",
			"--dataDir" }, description = "索引目录， 默认值: ${DEFAULT-VALUE}", defaultValue = "./data/wang-data")
	private String dataDir;
	
	@Option(names = { "-i",
			"--indexDir" }, description = "索引目录， 默认值: ${DEFAULT-VALUE}", defaultValue = "./data/wang-idx")
	private String indexDir;
	

	public static void main(String[] args) {
		CommandLine.call(new Main(), args);
	}
	 
	private void indexArticle(IndexWriter writer, Article article, int bookId, int articleId) throws IOException {
		StringBuilder sb=new StringBuilder("");
		for(BookPage page:article.getPages()){
			for(BookLine line:page.getLines()) {
				sb.append(line.getText());
			}
		}
		String content=sb.toString();
		String title=article.getTitle();
		Document d = new Document(); 
		Seg seg=Seg.getInstance();
		List<String> tokens=seg.seg(title);
		String sss=String.join(" ", tokens); 
		d.add(new TextField(IndexConstants.FIELD_TITLE, sss, Store.YES));
		
		tokens=seg.seg(content);
		sss=String.join(" ", tokens); 
		d.add(new TextField(IndexConstants.FIELD_CONTENT, sss, Store.NO));
 
		d.add(new StringField(IndexConstants.FIELD_BOOK_ID, bookId+"", Store.YES));
		d.add(new StringField(IndexConstants.FIELD_ARTICLE_ID, articleId+"", Store.YES));
		writer.addDocument(d);
	}

	@Override
	public Void call() throws Exception {  
		Gson gson=new Gson();
        Analyzer analyzer = new WhitespaceAnalyzer();
        Directory dir = FSDirectory.open(Paths.get(indexDir));
        IndexWriterConfig iwc = new IndexWriterConfig(analyzer);
        iwc.setOpenMode(OpenMode.CREATE);
        IndexWriter writer = new IndexWriter(dir, iwc);
		for(File subDir:new File(this.dataDir).listFiles()) {
			String dirName=subDir.getName();
			log.info("index dir {}", dirName);
			int bookId=Integer.valueOf(dirName.substring(dirName.length()-1));
			for(File file:subDir.listFiles()) {
				String fileName=file.getName();
				log.info("index file {}", fileName);
				int articleId=Integer.valueOf(fileName.substring(0, 2));
				String s=FileUtils.readFileToString(file, StandardCharsets.UTF_8);
				Article article=gson.fromJson(s, Article.class);
				this.indexArticle(writer, article, bookId, articleId);
			}
		}
		writer.close();
		log.info("writer closed");
		
		return null;
	}

}
