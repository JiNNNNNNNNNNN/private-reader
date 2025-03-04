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
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.security.SecureRandom;
import javax.net.ssl.SSLSocketFactory;
import java.security.cert.X509Certificate;

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
    private final SSLSocketFactory sslSocketFactory;

    public UniversalParser(final String url) {
        this.url = url;
        this.sslSocketFactory = createInsecureSSLSocketFactory();
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
                .sslSocketFactory(sslSocketFactory)
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
        LOG.debug("找到链接数量: " + links.size() + "，正在分析...");
        
        int processedLinks = 0;
        for (Element link : links) {
            String href = link.attr("abs:href");
            String title = link.text().trim();
            
            if (isChapterLink(href, title)) {
                chapters.add(new Chapter(title, href));
                LOG.debug("找到章节: " + title + " -> " + href);
            }
            
            processedLinks++;
            if (processedLinks % 100 == 0) {
                LOG.info(String.format("已处理 %d/%d 个链接，找到 %d 个章节", 
                    processedLinks, links.size(), chapters.size()));
            }
        }
        
        // 如果没有找到章节，尝试查找可能的章节目录页面
        if (chapters.isEmpty()) {
            LOG.info("直接解析未找到章节，尝试查找目录页面...");
            Elements catalogLinks = document.select("a:matches(目录|章节|卷章|分卷|分章)");
            LOG.debug("找到可能的目录链接数量: " + catalogLinks.size() + "，开始逐个尝试...");
            
            for (Element link : catalogLinks) {
                try {
                    String catalogUrl = link.attr("abs:href");
                    LOG.info("正在尝试解析目录页面: " + catalogUrl);
                    Document catalogDoc = Jsoup.connect(catalogUrl)
                        .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                        .timeout(10000)
                        .get();
                    Elements catalogChapters = catalogDoc.select("a[href]");
                    LOG.debug("目录页面中找到链接数量: " + catalogChapters.size() + "，开始分析...");
                    
                    int processedCatalogLinks = 0;
                    for (Element chapter : catalogChapters) {
                        String href = chapter.attr("abs:href");
                        String title = chapter.text().trim();
                        if (isChapterLink(href, title)) {
                            chapters.add(new Chapter(title, href));
                            LOG.debug("从目录页面找到章节: " + title + " -> " + href);
                        }
                        
                        processedCatalogLinks++;
                        if (processedCatalogLinks % 50 == 0) {
                            LOG.info(String.format("目录页面已处理 %d/%d 个链接，找到 %d 个章节", 
                                processedCatalogLinks, catalogChapters.size(), chapters.size()));
                        }
                    }
                    if (!chapters.isEmpty()) {
                        LOG.info(String.format("成功从目录页面解析到章节列表，共 %d 章", chapters.size()));
                        break;
                    }
                } catch (IOException e) {
                    LOG.warn("解析目录页面失败: " + e.getMessage() + "，尝试下一个目录链接");
                }
            }
        }
        
        if (chapters.isEmpty()) {
            LOG.warn("未能找到任何章节，请检查网页结构或尝试其他目录页面");
        } else {
            LOG.info(String.format("章节解析完成，共找到 %d 章，正在排序...", chapters.size()));
            // 可以在这里添加章节排序逻辑
        }
        
        return chapters;
    }

    @Override
    public String parseChapterContent(String chapterId) {
        LOG.debug("开始解析章节内容: " + chapterId);
        try {
            LOG.info("正在连接章节页面...");
            // 直接获取原始字节数据
            byte[] responseBytes = Jsoup.connect(chapterId)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7")
                .header("Accept-Language", "zh-CN,zh;q=0.9")
                .header("Cache-Control", "no-cache")
                .header("Pragma", "no-cache")
                .header("Referer", url)
                .header("Cookie", "")
                .timeout(15000)
                .maxBodySize(0)
                .followRedirects(true)
                .sslSocketFactory(sslSocketFactory)
                .ignoreContentType(true)
                .ignoreHttpErrors(true)
                .execute()
                .bodyAsBytes();

            // 尝试不同的编码解析内容
            Document chapterDoc = null;
            String[] encodings = {"UTF-8", "GBK", "GB2312", "GB18030", "BIG5"};
            String bestContent = null;
            String bestCharset = null;
            int minGarbageScore = Integer.MAX_VALUE;

            for (String encoding : encodings) {
                try {
                    String html = new String(responseBytes, encoding);
                    Document doc = Jsoup.parse(html, chapterId);
                    
                    // 获取正文内容
                    Element content = doc.selectFirst("div#content1");
                    if (content == null) {
                        content = doc.selectFirst("div.content_read");
                    }
                    if (content == null) {
                        content = doc.selectFirst("div.box_con #content");
                    }
                    if (content == null) {
                        content = TextDensityAnalyzer.findContentElement(doc);
                    }
                    
                    if (content != null) {
                        String text = content.text();
                        int garbageScore = calculateGarbageScore(text);
                        LOG.debug(String.format("编码 %s 的乱码评分: %d", encoding, garbageScore));
                        
                        if (garbageScore < minGarbageScore) {
                            minGarbageScore = garbageScore;
                            bestContent = text;
                            bestCharset = encoding;
                            chapterDoc = doc;
                        }
                    }
                } catch (Exception e) {
                    LOG.warn("使用编码 " + encoding + " 解析失败: " + e.getMessage());
                }
            }

            if (bestCharset != null) {
                LOG.info("选择最佳编码: " + bestCharset + "，乱码评分: " + minGarbageScore);
            } else {
                LOG.warn("未能找到合适的编码，使用默认UTF-8");
                chapterDoc = Jsoup.parse(new String(responseBytes, "UTF-8"), chapterId);
            }

            LOG.info("页面获取成功，开始分析正文内容...");
            // 首先尝试直接使用特定选择器
            Element content = chapterDoc.selectFirst("div#content1");
            if (content == null) {
                LOG.debug("尝试使用 content1 选择器失败，尝试其他选择器...");
                content = chapterDoc.selectFirst("div.content_read");
            }
            if (content == null) {
                LOG.debug("尝试使用 content_read 选择器失败，尝试其他选择器...");
                content = chapterDoc.selectFirst("div.box_con #content");
            }
            
            // 如果特定选择器没有找到内容，使用通用的文本密度分析
            if (content == null) {
                LOG.info("常用选择器均未找到内容，切换到智能分析模式...");
                content = TextDensityAnalyzer.findContentElement(chapterDoc);
            }

            if (content != null) {
                LOG.info("已找到正文内容，开始清理...");
                // 移除广告和无关内容
                content.select("script, style, a, iframe, div.adsbygoogle, .bottem, .bottem2").remove();
                
                String text = content.text();
                LOG.debug("原始内容长度: " + text.length() + " 字符");
                
                // 清理特定的广告文本和无关内容
                text = text.replaceAll("(?i)(广告|推广|http|www|com|net|org|xyz)[^，。！？]*", "")
                         .replaceAll("(?i)(八八中文网|88中文网|求书网|新笔趣阁|笔趣阁|顶点小说|番茄小说)[^，。！？]*", "")
                         .replaceAll("最新章节！", "")
                         .replaceAll("\\s*([，。！？])\\s*", "$1\n")  // 在标点符号后添加换行
                         .replaceAll("\\s+", "\n")  // 将多个空白字符替换为换行
                         .replaceAll("\\n{3,}", "\n\n")  // 限制最大连续换行数
                         .replaceAll("^\\s*第[0-9零一二三四五六七八九十百千万亿]+[章节卷集部篇].*$", "")  // 移除数字章节标题
                         .replaceAll("^\\s*[0-9]+[、.][^0-9]*$", "")  // 移除数字序号标题
                         .replaceAll("^\\s*第[0-9零一二三四五六七八九十百千万亿]+回.*$", "")  // 移除回数标题
                         .replaceAll("^\\s*[序楔终][章话].*$", "")  // 移除特殊章节标题
                         .replaceAll("^\\s*[前序楔引]言.*$", "")  // 移除前言等
                         .replaceAll("^\\s*[后终]记.*$", "")  // 移除后记等
                         .replaceAll("^\\s*[卷部篇][0-9零一二三四五六七八九十百千万亿]+.*$", "")  // 移除卷标题
                         .replaceAll("^\\s*[上中下]篇.*$|^\\s*番外.*$|^\\s*特别篇.*$|^\\s*外传.*$", "")  // 移除特殊篇章标题
                         .replaceAll("^\\s*[早中午晚]章.*$|^\\s*[春夏秋冬]章.*$", "")  // 移除时间相关章节标题
                         .replaceAll("^\\s*(间|幕)?插.*$", "")  // 移除插入章节标题
                         .trim();
                
                String formatted = TextFormatter.format(text);
                LOG.info(String.format("内容处理完成，最终长度: %d 字符", formatted.length()));
                return formatted;
            }

            LOG.warn("未能找到有效的正文内容，可能是页面结构发生变化");
            return "无法识别章节内容，请检查网页结构是否变化";
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

    private SSLSocketFactory createInsecureSSLSocketFactory() {
        try {
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, new TrustManager[]{new X509TrustManager() {
                public void checkClientTrusted(X509Certificate[] chain, String authType) {}
                public void checkServerTrusted(X509Certificate[] chain, String authType) {}
                public X509Certificate[] getAcceptedIssuers() {
                    return new X509Certificate[0];
                }
            }}, new SecureRandom());
            return sslContext.getSocketFactory();
        } catch (Exception e) {
            throw new RuntimeException("创建SSL Socket Factory失败", e);
        }
    }

    private int calculateGarbageScore(String text) {
        if (text == null || text.isEmpty()) {
            return Integer.MAX_VALUE;
        }

        int score = 0;
        char[] chars = text.toCharArray();
        
        // 1. 检查特殊乱码字符
        for (char c : chars) {
            if (c == '\uFFFD' || c == '?' || c == '□' || c == '■' || 
                c == '\u0000' || c == '\uFFFF' || 
                (c >= '\uFDD0' && c <= '\uFDEF') ||
                (c >= '\uFFFE' && c <= '\uFFFF')) {
                score += 10;
            }
        }
        
        // 2. 检查连续的非中文字符
        int consecutiveNonChinese = 0;
        for (char c : chars) {
            if (!isValidChinese(c) && c > 0x7F) {
                consecutiveNonChinese++;
                if (consecutiveNonChinese > 2) {
                    score += 5;
                }
            } else {
                consecutiveNonChinese = 0;
            }
        }
        
        // 3. 检查中文标点符号的合理性
        boolean hasChinese = false;
        boolean hasChinesePunctuation = false;
        for (char c : chars) {
            if (Character.UnicodeBlock.of(c) == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS) {
                hasChinese = true;
            }
            if (Character.UnicodeBlock.of(c) == Character.UnicodeBlock.CJK_SYMBOLS_AND_PUNCTUATION) {
                hasChinesePunctuation = true;
            }
        }
        if (hasChinese && !hasChinesePunctuation) {
            score += 50;  // 有中文但没有中文标点，可能是编码问题
        }
        
        return score;
    }
    
    private boolean isValidChinese(char c) {
        // 检查是否是有效的中文字符
        Character.UnicodeBlock ub = Character.UnicodeBlock.of(c);
        return ub == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
            || ub == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS
            || ub == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A
            || ub == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B
            || ub == Character.UnicodeBlock.CJK_SYMBOLS_AND_PUNCTUATION
            || ub == Character.UnicodeBlock.HALFWIDTH_AND_FULLWIDTH_FORMS
            || ub == Character.UnicodeBlock.GENERAL_PUNCTUATION;
    }
}