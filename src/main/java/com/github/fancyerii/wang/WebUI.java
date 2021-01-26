package com.github.fancyerii.wang;

import static spark.Spark.get;
import static spark.Spark.post;
import static spark.Spark.notFound;
import static spark.Spark.staticFiles;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.Callable;
import java.util.regex.Pattern;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.text.StringEscapeUtils;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.BooleanQuery.Builder;
import org.apache.lucene.search.BoostQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.PhraseQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.FSDirectory;
import com.github.fancyerii.wang.tool.Seg;
import com.github.fancyerii.wang.index.IndexConstants;
import com.github.fancyerii.wang.process.Article;
import com.github.fancyerii.wang.process.BookLine;
import com.github.fancyerii.wang.process.BookPage;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import spark.ModelAndView;
import spark.Request;
import spark.Response;
import spark.TemplateViewRoute;

@Slf4j
public final class WebUI implements Callable<Void> {
	@Option(names = { "--indexDir" }, description = "索引目录， 默认值: ${DEFAULT-VALUE}")
	private String indexDir = "./data/wang-idx";

	@Option(names = { "-p", "--num-page" }, description = "每页条数，默认值: ${DEFAULT-VALUE}")
	private int numPage = 10;

	@Option(names = { "-w", "--page-window" }, description = "翻页窗口，默认值: ${DEFAULT-VALUE}")
	private int pageWindow = 3;

	@Option(names = { "-d", "--data-dir" }, description = "数据，默认值: ${DEFAULT-VALUE}")
	private String dataDir = "./data/wang-data";

	@Option(names = { "-i", "--img-dir" }, description = "图片数据，默认值: ${DEFAULT-VALUE}")
	private String imgDir = "./data/wang";
	
	@Option(names = { "--disable-modify" }, description = "禁止修改，默认值: ${DEFAULT-VALUE}")
	private boolean disableModify = false;
	
	private String[] bookNames=new String[]	{
		"汪曾祺全集一 小说卷",
		"汪曾祺全集二 小说卷",
		"汪曾祺全集三 散文卷",
		"汪曾祺全集四 散文卷",
		"汪曾祺全集五 散文卷",
		"汪曾祺全集六 散文卷",
		"汪曾祺全集七 戏剧卷",
		"汪曾祺全集八 其它",
	};
	private IndexSearcher searcher;
	private IndexReader reader;

	@Option(names = { "-h", "--hl-number" }, description = "标红连续行数，默认值: ${DEFAULT-VALUE}")
	private int hlLineNumber = 3;

	private Map<String, String> articleMap;

	private Gson gson = new Gson();
	private JsonParser parser = new JsonParser();

	public static void main(final String[] args) {
		CommandLine.call(new WebUI(), args);

	}

	private void init() throws IOException {
		reader = DirectoryReader.open(FSDirectory.open(Paths.get(indexDir)));
		searcher = new IndexSearcher(reader);

		articleMap = new HashMap<>();
		File[] subDirs = new File(dataDir).listFiles();
		for (File subDir : subDirs) {
			String bookName = subDir.getName();
			int bookId = Integer.valueOf(bookName.substring(bookName.length() - 1));
			for (File articleFile : subDir.listFiles()) {
				String articleName = articleFile.getName();
				int articleId = Integer.valueOf(articleName.substring(0, 2));
				articleMap.put(bookId + "," + articleId, articleFile.getAbsolutePath());
			}
		}
	}

	private Article getArticle(int bookId, int articleId) throws IOException {
		return getArticle(bookId + "", articleId + "");
	}

	private Article getArticle(String bookId, String articleId) throws IOException {
		String filePath = articleMap.get(bookId + "," + articleId);
		if (filePath == null) {
			return null;
		}

		String s = FileUtils.readFileToString(new File(filePath), StandardCharsets.UTF_8);
		return gson.fromJson(s, Article.class);
	}

	@Override
	public Void call() throws Exception {
		init();
		staticFiles.location("/public");
		get("/article", viewPage(), new ThymeleafTemplateEngine());
		get("/search", search(), new ThymeleafTemplateEngine());
		get("/book", book(), new ThymeleafTemplateEngine());
		
		post("/ajax/updateLine", "application/json", (req, res) -> {
			if(disableModify) return "";
			String bd = req.body();
			JsonObject jo = parser.parse(bd).getAsJsonObject();
			String oldTxt = jo.get("old").getAsString();
			String newTxt = jo.get("new").getAsString();
			String name = jo.get("name").getAsString();
			int bookId = Integer.parseInt(jo.get("bookId").getAsString());
			int articleId = Integer.parseInt(jo.get("articleId").getAsString());
			int p = Integer.parseInt(jo.get("p").getAsString());
			int lineNo = Integer.valueOf(name);

			Map<String, Object> resp = new HashMap<>();
			resp.put("succ", true);
			resp.put("update", true);
			if (oldTxt.equals(newTxt)) {
				resp.put("update", false);
				return resp;
			}

			if (oldTxt.contains("<span class='hl'>")) {
				resp.put("update", false);
				return resp;
			}

			Article article = this.getArticle(bookId, articleId);
			if (article == null) {
				Map<String, Object> model = new HashMap<>();
				model.clear();
				model.put("message", "找不到 " + bookId + "," + articleId);
				return new ModelAndView(model, "error");
			}

			BookPage page = article.getPages().get(p - 1);
			if (newTxt.isEmpty()) {
				log.info("delete page[{}] lineIdx[{}], {}", p, lineNo, oldTxt);
				page.getLines().remove(lineNo);
			} else {

				BookLine line = page.getLines().get(lineNo);
				if (newTxt.contains("_INS_AFTER_")) {
					String[] arr = newTxt.split("_INS_AFTER_");
					log.info("insertAfter page[{}] lineIdx[{}] from {} -> {} and insert {}", p, lineNo, oldTxt, arr[0],
							arr[1]);
					line.setText(arr[0]);
					BookLine newLine = new BookLine();
					newLine.setText(arr[1]);
					page.getLines().add(lineNo + 1, newLine);
				} else if (newTxt.contains("_INS_BEFORE_")) {
					String[] arr = newTxt.split("_INS_BEFORE_");
					log.info("insertBefore page[{}] lineIdx[{}] from {} -> {} and insert {}", p, lineNo, oldTxt, arr[1],
							arr[0]);
					line.setText(arr[1]);
					BookLine newLine = new BookLine();
					newLine.setText(arr[0]);
					page.getLines().add(lineNo, newLine);
				} else {
					log.info("update page[{}] lineIdx[{}] from {} -> {}", p, lineNo, oldTxt, newTxt);
					line.setText(newTxt);
				}

			}
			// log.info("page: {}",gson.toJson(page));
			String filePath = articleMap.get(bookId + "," + articleId);
			FileUtils.write(new File(filePath), gson.toJson(article), StandardCharsets.UTF_8);
			return resp;
		}, gson::toJson);
		// staticFiles.externalLocation(this.imgDir);
		get("/imgs/*/*", (request, response) -> {
			String[] paths = request.splat();
			if (paths.length != 2) {
				notFound("<html><body><h1>img not found</h1></body></html>");
			}
			String filePath = this.imgDir + "/book" + paths[0] + "/" + paths[1];
			File imgFile = new File(filePath);
			if (!imgFile.exists()) {
				notFound("<html><body><h1>img not found</h1></body></html>");
			}
			response.raw().setContentType("image/png");
			response.raw().getOutputStream().write(FileUtils.readFileToByteArray(imgFile));
			response.raw().getOutputStream().close();
			return null;
		});
		return null;
	}

	private void doSearch(Map<String, Object> model, String q, int page, int numPage) throws Exception {
		String qStr = q;
		List<String> words = Seg.getInstance().seg(q);
		Builder builder = new BooleanQuery.Builder();
		int minShould = (int) Math.max(1, words.size() / 1.5);
		for (String word : words) {
			Query boostedTermQuery = new BoostQuery(new TermQuery(new Term(IndexConstants.FIELD_TITLE, word)), 2);
			builder.add(boostedTermQuery, Occur.SHOULD);
			builder.add(new TermQuery(new Term(IndexConstants.FIELD_CONTENT, word)), Occur.SHOULD);
		}
		// ngram
		for (int n = 2; n < 4; n++) {

			for (int i = 0; i + n <= words.size(); i++) {
				PhraseQuery.Builder pb1 = new PhraseQuery.Builder();
				PhraseQuery.Builder pb2 = new PhraseQuery.Builder();
				for (int j = i; j < i + n; j++) {
					pb1.add(new Term(IndexConstants.FIELD_TITLE, words.get(j)), j);
					pb2.add(new Term(IndexConstants.FIELD_CONTENT, words.get(j)), j);
				}
				PhraseQuery pq1 = pb1.build();
				builder.add(new BoostQuery(pq1, 1.5f * n), Occur.SHOULD);

				PhraseQuery pq2 = pb2.build();
				builder.add(new BoostQuery(pq2, n), Occur.SHOULD);
			}

		}
		BooleanQuery query = builder.setMinimumNumberShouldMatch(minShould).build();

		log.info("q: {}, page: {}, qStr: {}", query, page, qStr);
		TopDocs results = searcher.search(query, page * numPage);
		ScoreDoc[] hits = results.scoreDocs;
		int numTotalHits = Math.toIntExact(results.totalHits);
		int start = (page - 1) * numPage;
		int end = Math.min(numTotalHits, page * numPage);
		int totalPage = (Math.min(10000, numTotalHits) - 1) / numPage + 1;
		if (numTotalHits == 0) {
			totalPage = 0;
		}
		List<SearchItem> items = new ArrayList<>(numPage);
		model.put("items", items);
		model.put("curPage", page);
		model.put("totalPage", totalPage);
		List<Integer> pageNumbers = new ArrayList<>();
		int minPage = Math.max(1, page - pageWindow);
		int maxPage = Math.min(totalPage, page + pageWindow);
		for (int i = minPage; i <= maxPage; i++) {
			pageNumbers.add(i);
		}
		model.put("pageNumbers", pageNumbers);
		for (int i = start; i < end; i++) {
			Document doc = searcher.doc(hits[i].doc);
			int bookId = Integer.valueOf(doc.get(IndexConstants.FIELD_BOOK_ID));
			int articleId = Integer.valueOf(doc.get(IndexConstants.FIELD_ARTICLE_ID));
			Article article = this.getArticle(bookId, articleId);
			Pair<String, Integer> pair = this.getHighLightContent(article, words);
			SearchItem item = new SearchItem(this.highLightString(article.getTitle(), words), pair.getLeft(), bookId,
					articleId, pair.getRight());
			items.add(item);
		}
	}

	private String highLightString(String title, List<String> words) {
		for (String word : words) {
			title = title.replaceAll(Pattern.quote(word), this.hl(word));
		}

		return title;
	}

	private Pair<String, Integer> getHighLightContent(Article article, List<String> words) {
		int bestPage = 1;
		int bestScore = 0;
		String bestLines = "";
		for (int i = 0; i < article.getPages().size(); i++) {
			BookPage page = article.getPages().get(i);
			LinkedList<String> lines = new LinkedList<>();
			for (BookLine line : page.getLines()) {
				lines.addLast(line.getText());
				if (lines.size() > this.hlLineNumber) {
					lines.removeFirst();
				}
				int curScore = this.score(lines, words);
				if (curScore > bestScore) {
					bestScore = curScore;
					bestPage = i + 1;
					bestLines = this.hlLines(lines, words);
				}
			}
		}

		return Pair.of(bestLines, bestPage);
	}

	private String hlLines(LinkedList<String> lines, List<String> words) {
		StringBuilder sb = new StringBuilder("");
		sb.append("<p class='hlline'>\n");
		for (String line : lines) {
			sb.append(this.highLightString(line, words));
		}
		sb.append("</p>\n");
		return sb.toString();
	}

	private int score(LinkedList<String> lines, List<String> words) {
		int score = 0;
		for (String line : lines) {
			for (String word : words) {
				if (line.contains(word)) {
					score++;
				}
			}
		}

		return score;
	}
	
	private TemplateViewRoute book() {
		return new TemplateViewRoute() {
			@Override
			public ModelAndView handle(Request request, Response response) throws Exception {
				Map<String, Object> model = new HashMap<>();
				int bookId=1;
				try {
					bookId=Integer.valueOf(request.queryParams("bookId"));
				}catch(Exception e) {}
				if(bookId>8 || bookId<1) {
					bookId=1;
				}
				List<ArticleItem> articles=new ArrayList<>();
				model.put("articles", articles);
				for(Entry<String,String> entry:articleMap.entrySet()) {
					int bId=Integer.valueOf(entry.getKey().split(",")[0]);
					if(bId!=bookId) continue;
					File f=new File(entry.getValue());
					String fn=f.getName();
					int id=Integer.valueOf(fn.substring(0, 2));
					String name=fn.substring(3);
					articles.add(new ArticleItem(id, name));
				}
				Collections.sort(articles, (a1,a2)->a1.getArticleId()-a2.getArticleId());
				model.put("bookId", bookId);
				model.put("bookName", bookNames[bookId-1]);
				return new ModelAndView(model, "book"); // located in resources/templates
			}
		};
	}
	private TemplateViewRoute search() {
		return new TemplateViewRoute() {
			@Override
			public ModelAndView handle(Request request, Response response) throws Exception {
				Map<String, Object> model = new HashMap<>();
				String q = request.queryParams("q");
				if (q == null) {
					q = "";
				}
				q = q.trim();
				String pageStr = request.queryParams("page");
				int page = 1;
				try {
					page = Integer.valueOf(pageStr);
				} catch (Exception e) {
				}

				model.put("q", q);

				if (!StringUtils.isEmpty(q)) {
					doSearch(model, q, page, numPage);
				}

				return new ModelAndView(model, "search"); // located in resources/templates
			}

		};
	}

	private String hl(String word) {
		return "<span class='hl'>" + word + "</span>";
	}

	private TemplateViewRoute viewPage() {

		return new TemplateViewRoute() {

			private String hlString(String s, List<String> words) {
				for (String word : words) {
					s = s.replaceAll(Pattern.quote(word), hl(word));
				}

				return s;
			}

			@Override
			public ModelAndView handle(Request request, Response response) throws Exception {
				Map<String, Object> model = new HashMap<>();

				String book = request.queryParams("book");
				String articleId = request.queryParams("article");
				String p = request.queryParams("p");
				String kw = request.queryParams("kw");
				if (kw == null)
					kw = "";
				kw = kw.trim();
				List<String> kWords = Seg.getInstance().seg(kw);
				boolean isSearch = !kw.isEmpty();
				int page = 1;
				try {
					page = Integer.valueOf(p);
				} catch (Exception e) {
				}

				Article article = getArticle(book, articleId);
				if (article == null) {
					model.clear();
					model.put("message", "找不到 " + book + "," + articleId);
					return new ModelAndView(model, "error");
				}

				String title = article.getTitle();

				int imageId = page - 1 + article.getStartPageNo();
				if (imageId > article.getEndPageNo()) {
					model.clear();
					model.put("message", "找不到image " + imageId);
					return new ModelAndView(model, "error");
				}

				BookPage bp = article.getPages().get(page - 1);
				title = StringEscapeUtils.escapeHtml4(title);
				for (BookLine line : bp.getLines()) {
					line.setText(StringEscapeUtils.escapeHtml4(line.getText()));
				}
				if (isSearch) {
					title = this.hlString(title, kWords);
					for (BookLine line : bp.getLines()) {
						line.setText(this.hlString(line.getText(), kWords));
					}
				}
				model.put("bp", bp);
				model.put("title", title);
				model.put("imgId", imageId);
				model.put("bookId", book);
				model.put("articleId", articleId);
				model.put("isSearch", isSearch);
				model.put("disableModify", disableModify);
				model.put("kw", kw);
				List<Integer> pageNumbers = new ArrayList<>();
				for (int i = page - 2; i <= page + 2; i++) {
					if (i > 0 && i <= article.getEndPageNo() - article.getStartPageNo() + 1) {
						pageNumbers.add(i);
					}
				}

				model.put("pageNumbers", pageNumbers);
				model.put("curPage", page);
				return new ModelAndView(model, "article"); // located in resources/templates
			}

		};
	}

}
