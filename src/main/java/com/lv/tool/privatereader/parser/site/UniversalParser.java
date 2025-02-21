package com.lv.tool.privatereader.parser.site;

import com.intellij.openapi.diagnostic.Logger;
import com.lv.tool.privatereader.parser.NovelParser;
import com.lv.tool.privatereader.parser.common.ChapterAnalyzer;
import com.lv.tool.privatereader.parser.common.TextDensityAnalyzer;
import com.lv.tool.privatereader.parser.common.MetadataAnalyzer;
import com.lv.tool.privatereader.parser.common.TextFormatter;
import com.lv.tool.privatereader.parser.common.ChapterTitleUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 通用网络小说解析器，用于智能解析网络小说网站
 */
public final class UniversalParser implements NovelParser {
    private static final Logger LOG = Logger.getInstance(UniversalParser.class);
    private static final Pattern CHAPTER_PATTERN = Pattern.compile(
        "^(?:第)?[0-9零一二三四五六七八九十百千万]+[章节卷集].*$|^[0-9]+[、.][^\\d].*$"
    );
    private final String url;
    private Document document;

    public UniversalParser(final String url) {
        this.url = url;
        LOG.info("开始解析网址: " + url);
        try {
            LOG.debug("尝试连接网址...");
            // 使用系统属性设置代理
            System.setProperty("http.proxyHost", "");
            System.setProperty("http.proxyPort", "");
            System.setProperty("https.proxyHost", "");
            System.setProperty("https.proxyPort", "");
            
            this.document = Jsoup.connect(url)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .timeout(10000)
                .followRedirects(true)
                .ignoreContentType(true)
                .ignoreHttpErrors(true)
                .get();
            LOG.debug("成功获取页面内容，长度: " + document.html().length());
        } catch (IOException e) {
            LOG.error("连接网址失败: " + url, e);
            throw new RuntimeException("无法连接到网址: " + url, e);
        }
    }

    @Override
    public String getTitle() {
        LOG.debug("开始识别书籍标题...");
        String title = MetadataAnalyzer.findTitle(document);
        LOG.info("识别到书籍标题: " + title);
        return title;
    }

    @Override
    public String getAuthor() {
        LOG.debug("开始识别作者信息...");
        String author = MetadataAnalyzer.findAuthor(document);
        LOG.info("识别到作者: " + author);
        return author;
    }

    @Override
    public List<Chapter> parseChapterList() {
        LOG.debug("开始解析章节列表...");
        List<Chapter> chapters = new ArrayList<>();
        Elements links = document.select("a[href]");
        LOG.debug("找到链接数量: " + links.size());
        
        for (Element link : links) {
            String href = link.attr("abs:href");
            String title = link.text().trim();
            
            if (isChapterLink(href, title)) {
                chapters.add(new Chapter(title, href));
                LOG.debug("找到章节: " + title + " -> " + href);
            }
        }
        
        // 如果没有找到章节，尝试查找可能的章节目录页面
        if (chapters.isEmpty()) {
            LOG.info("直接解析未找到章节，尝试查找目录页面...");
            Elements catalogLinks = document.select("a:matches(目录|章节|卷章|分卷|分章)");
            LOG.debug("找到可能的目录链接数量: " + catalogLinks.size());
            
            for (Element link : catalogLinks) {
                try {
                    String catalogUrl = link.attr("abs:href");
                    LOG.debug("尝试解析目录页面: " + catalogUrl);
                    Document catalogDoc = Jsoup.connect(catalogUrl)
                        .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                        .timeout(10000)
                        .get();
                    Elements catalogChapters = catalogDoc.select("a[href]");
                    LOG.debug("目录页面中找到链接数量: " + catalogChapters.size());
                    
                    for (Element chapter : catalogChapters) {
                        String href = chapter.attr("abs:href");
                        String title = chapter.text().trim();
                        if (isChapterLink(href, title)) {
                            chapters.add(new Chapter(title, href));
                            LOG.debug("从目录页面找到章节: " + title + " -> " + href);
                        }
                    }
                    if (!chapters.isEmpty()) {
                        LOG.info("成功从目录页面解析到章节列表，数量: " + chapters.size());
                        break;
                    }
                } catch (IOException e) {
                    LOG.warn("解析目录页面失败: " + e.getMessage());
                }
            }
        }
        
        LOG.info("章节解析完成，总数: " + chapters.size());
        return chapters;
    }

    @Override
    public String parseChapterContent(String chapterId) {
        LOG.debug("开始解析章节内容: " + chapterId);
        try {
            LOG.debug("尝试获取章节页面...");
            Document chapterDoc = Jsoup.connect(chapterId)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7")
                .header("Accept-Language", "zh-CN,zh;q=0.9")
                .header("Cache-Control", "no-cache")
                .header("Pragma", "no-cache")
                .header("Referer", url)
                .header("Cookie", "")  // 清空 Cookie
                .timeout(15000)
                .maxBodySize(0)
                .followRedirects(true)
                .get();

            LOG.debug("使用文本密度分析查找正文内容...");
            // 首先尝试直接使用 88xiaoshuo.net 的特定选择器
            Element content = chapterDoc.selectFirst("div#content1");
            if (content == null) {
                content = chapterDoc.selectFirst("div.content_read");
            }
            if (content == null) {
                content = chapterDoc.selectFirst("div.box_con #content");
            }
            
            // 如果特定选择器没有找到内容，使用通用的文本密度分析
            if (content == null) {
                content = TextDensityAnalyzer.findContentElement(chapterDoc);
            }

            if (content != null) {
                // 移除广告和无关内容
                content.select("script, style, a, iframe, div.adsbygoogle, .bottem, .bottem2").remove();
                
                String text = content.text();
                LOG.debug("找到正文内容，长度: " + text.length());
                
                // 清理特定的广告文本和无关内容
                text = text.replaceAll("(?i)(广告|推广|http|www|com|net|org|xyz)[^，。！？]*", "")
                         .replaceAll("(?i)(八八中文网|88中文网|求书网|新笔趣阁|笔趣阁|顶点小说|番茄小说)[^，。！？]*", "")
                         .replaceAll("最新章节！", "")
                         .replaceAll("\\s*([，。！？])\\s*", "$1\n")  // 在标点符号后添加换行
                         .replaceAll("\\s+", "\n")  // 将多个空白字符替换为换行
                         .replaceAll("\\n{3,}", "\n\n")  // 限制最大连续换行数
                         .replaceAll("^\\s*第[一二三四五六七八九十百千万]+[章节].*$", "")  // 移除重复的章节标题
                         .trim();
                
                String formatted = TextFormatter.format(text);
                LOG.debug("格式化后内容长度: " + formatted.length());
                return formatted;
            }

            LOG.warn("未能找到有效的正文内容");
            return "无法识别章节内容";
        } catch (IOException e) {
            LOG.error("获取章节内容失败: " + chapterId, e);
            throw new RuntimeException("获取章节内容失败：" + e.getMessage(), e);
        }
    }

    private boolean isChapterLink(String href, String title) {
        if (href == null || title == null || href.isEmpty() || title.isEmpty()) {
            return false;
        }

        // 1. URL特征判断
        boolean urlMatch = href.contains("/chapter/") || 
                          href.contains("/read/") ||
                          href.contains("/book/") ||
                          href.matches(".*/(\\d+).(html|htm|shtml|aspx|php)$") ||
                          href.matches(".*/chapter_\\d+.*") ||
                          href.matches(".*/c\\d+.*") ||
                          href.matches(".*/\\d+/\\d+.*");

        // 2. 标题特征判断
        boolean titleMatch = ChapterTitleUtils.isChapterTitle(title);

        // 3. 智能分析
        if (!urlMatch && !titleMatch) {
            // 检查URL中的数字序列
            boolean hasSequentialNumbers = href.matches(".*\\d+.*") &&
                                        !href.contains("javascript") &&
                                        !href.contains("login") &&
                                        !href.contains("register");
            
            // 检查标题长度和内容
            boolean titleLengthValid = title.length() >= 2 && title.length() <= 50;
            boolean titleHasValidChars = !title.contains("登录") && 
                                       !title.contains("注册") &&
                                       !title.contains("首页") &&
                                       !title.contains("最新") &&
                                       !title.contains("排行");
            
            // 如果URL包含序列数字且标题看起来合理，认为是章节链接
            if (hasSequentialNumbers && titleLengthValid && titleHasValidChars) {
                LOG.debug("通过智能分析识别到章节链接 - 标题: " + title);
                return true;
            }
        }

        if (urlMatch || titleMatch) {
            LOG.debug("识别到章节链接 - 标题: " + title + ", URL: " + href + 
                     " (URL匹配: " + urlMatch + ", 标题匹配: " + titleMatch + ")");
        }

        return urlMatch || titleMatch;
    }
}