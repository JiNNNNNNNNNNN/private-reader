package com.lv.tool.privatereader.parser.site;

import com.intellij.openapi.diagnostic.Logger;
import com.lv.tool.privatereader.exception.PrivateReaderException;
import com.lv.tool.privatereader.parser.NovelParser;
import com.lv.tool.privatereader.parser.common.ChapterAnalyzer;
import com.lv.tool.privatereader.parser.common.TextDensityAnalyzer;
import com.lv.tool.privatereader.parser.common.MetadataAnalyzer;
import com.lv.tool.privatereader.parser.common.TextFormatter;
import com.lv.tool.privatereader.parser.common.ChapterTitleUtils;
import com.lv.tool.privatereader.util.SafeHttpRequestExecutor;
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
    private boolean initialized = false;
    private IOException lastInitError = null;

    public UniversalParser(final String url) {
        this.url = url;
        this.sslSocketFactory = createInsecureSSLSocketFactory();
        LOG.info("创建UniversalParser实例: " + url);
        // 不在构造函数中加载网页，改为延迟加载
    }

    /**
     * 初始化解析器，加载网页内容
     *
     * @return 是否初始化成功
     */
    private boolean initialize() {
        if (initialized) {
            return document != null;
        }

        initialized = true; // 标记为已尝试初始化，无论成功与否

        LOG.info("开始初始化解析器并加载网页: " + url);
        try {
            LOG.debug("尝试连接网址...");
            // 使用系统属性设置代理
            System.setProperty("http.proxyHost", "");
            System.setProperty("http.proxyPort", "");
            System.setProperty("https.proxyHost", "");
            System.setProperty("https.proxyPort", "");

            // 使用安全的HTTP请求执行器，带有重试机制
            String htmlContent = SafeHttpRequestExecutor.executeGetRequest(url);

            // 添加显式的 null 检查
            if (htmlContent == null) {
                LOG.error("SafeHttpRequestExecutor.executeGetRequest返回null，URL: " + url);
                lastInitError = new IOException("获取页面内容失败 (返回 null): " + url);
                return false;
            }

            // 使用Jsoup解析获取的HTML内容
            this.document = Jsoup.parse(htmlContent, url);
            LOG.info("成功获取页面内容，长度: " + document.html().length());
            return true;
        } catch (IOException e) {
            LOG.error("连接网址失败: " + url + ", 错误: " + e.getMessage(), e);
            lastInitError = e;
            return false;
        } catch (Exception e) {
            LOG.error("初始化解析器时发生意外错误: " + url + ", 错误: " + e.getMessage(), e);
            lastInitError = new IOException("初始化解析器时发生意外错误: " + e.getMessage(), e);
            return false;
        }
    }

    /**
     * 确保解析器已初始化
     *
     * @throws IOException 如果初始化失败
     */
    private void ensureInitialized() throws IOException {
        if (!initialized) {
            if (!initialize()) {
                throw lastInitError != null ? lastInitError : new IOException("初始化解析器失败，原因未知");
            }
        } else if (document == null && lastInitError != null) {
            throw lastInitError;
        }
    }

    @Override
    public String getTitle() {
        LOG.debug("开始识别书籍标题...");
        try {
            // 确保解析器已初始化
            ensureInitialized();

            String title = MetadataAnalyzer.findTitle(document);
            LOG.info("识别到书籍标题: " + title);
            return title;
        } catch (IOException e) {
            LOG.error("获取书籍标题时发生错误: " + e.getMessage(), e);
            // 从URL中提取一个基本标题作为后备
            String urlPath = url.replaceAll("https?://[^/]+/", "");
            String[] pathSegments = urlPath.split("/");
            if (pathSegments.length > 0) {
                String lastSegment = pathSegments[pathSegments.length - 1];
                if (lastSegment.contains(".")) {
                    lastSegment = lastSegment.substring(0, lastSegment.lastIndexOf('.'));
                }
                if (!lastSegment.isEmpty()) {
                    LOG.debug("初始化失败，从URL中提取到标题: " + lastSegment);
                    return lastSegment;
                }
            }
            return url;
        }
    }

    @Override
    public String getAuthor() {
        LOG.debug("开始识别作者信息...");
        try {
            // 确保解析器已初始化
            ensureInitialized();

            String author = MetadataAnalyzer.findAuthor(document);
            LOG.info("识别到作者: " + author);
            return author;
        } catch (IOException e) {
            LOG.error("获取作者信息时发生错误: " + e.getMessage(), e);
            return "未知作者";
        }
    }

    @Override
    public List<Chapter> parseChapterList() {
        LOG.debug("开始解析章节列表...");
        List<Chapter> chapters = new ArrayList<>();

        try {
            // 确保解析器已初始化
            ensureInitialized();

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
        } catch (IOException e) {
            LOG.error("解析章节列表时发生错误: " + e.getMessage(), e);
            // 返回空列表，不抛出异常
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

                    // 使用安全的HTTP请求执行器
                    String catalogHtml = SafeHttpRequestExecutor.executeGetRequest(catalogUrl);

                    Document catalogDoc = Jsoup.parse(catalogHtml, catalogUrl);
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
            // 使用同步方法实现
            return parseChapterContentInternal(chapterId);
        } catch (Exception e) {
            LOG.error("解析章节内容时发生错误: " + e.getMessage(), e);
            throw new PrivateReaderException(
                "解析章节内容失败: " + e.getMessage(),
                e,
                PrivateReaderException.ExceptionType.PARSE_ERROR
            );
        }
    }

    private String parseChapterContentInternal(String chapterId) {
        try {
            LOG.info("正在连接章节页面...");

            // 使用安全的HTTP请求执行器
            String html = SafeHttpRequestExecutor.executeGetRequest(chapterId);

            LOG.info("成功获取章节页面内容，长度: " + html.length());

            // 尝试不同的编码解析内容
            Document chapterDoc = null;
            String[] encodings = {"UTF-8", "GBK", "GB2312", "GB18030", "BIG5"};
            String bestContent = null;
            String bestCharset = null;
            int minGarbageScore = Integer.MAX_VALUE;

            for (String encoding : encodings) {
                try {
                    // 使用获取到的HTML字符串创建Document对象
                    Document doc = Jsoup.parse(html, chapterId);
                    String content = extractContent(doc);

                    if (content != null && !content.isEmpty()) {
                        int garbageScore = calculateGarbageScore(content);
                        if (garbageScore < minGarbageScore) {
                            minGarbageScore = garbageScore;
                            bestContent = content;
                            bestCharset = encoding;
                            chapterDoc = doc;
                        }
                    }
                } catch (Exception e) {
                    LOG.warn("使用" + encoding + "解析失败: " + e.getMessage());
                }
            }

            if (bestContent == null || bestContent.isEmpty()) {
                throw new PrivateReaderException(
                    "无法解析章节内容，请检查网页格式或编码",
                    PrivateReaderException.ExceptionType.PARSE_ERROR
                );
            }

            LOG.info("成功解析章节内容，使用编码: " + bestCharset);
            return bestContent;
        } catch (IOException e) {
            throw new PrivateReaderException(
                "获取章节内容失败: " + e.getMessage(),
                e,
                PrivateReaderException.ExceptionType.NETWORK_ERROR
            );
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

    private String extractContent(Document doc) {
        try {
            // 首先尝试直接使用特定选择器
            Element content = doc.selectFirst("div#content1");
            if (content == null) {
                LOG.debug("尝试使用 content1 选择器失败，尝试其他选择器...");
                content = doc.selectFirst("div.content_read");
            }
            if (content == null) {
                LOG.debug("尝试使用 content_read 选择器失败，尝试其他选择器...");
                content = doc.selectFirst("div.box_con #content");
            }

            // 如果特定选择器没有找到内容，使用通用的文本密度分析
            if (content == null) {
                LOG.info("常用选择器均未找到内容，切换到智能分析模式...");
                content = TextDensityAnalyzer.findContentElement(doc);
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
        } catch (Exception e) {
            LOG.error("提取内容时发生错误", e);
        }

        return null;
    }

    /**
     * 计算文本的乱码评分
     * 分数越低表示乱码可能性越小
     */
    private int calculateGarbageScore(String text) {
        if (text == null || text.isEmpty()) {
            return Integer.MAX_VALUE;
        }

        int score = 0;
        // 统计不可打印字符
        for (char c : text.toCharArray()) {
            if (!Character.isLetterOrDigit(c) && !Character.isWhitespace(c) && !isPunctuationMark(c)) {
                score++;
            }
        }

        // 检查中文字符比例
        long chineseCount = text.chars()
            .filter(c -> Character.UnicodeScript.of(c) == Character.UnicodeScript.HAN)
            .count();
        double chineseRatio = (double) chineseCount / text.length();
        if (chineseRatio < 0.5) {  // 如果中文字符比例过低
            score += 1000;
        }

        // 检查常见乱码特征
        if (text.contains("\uFFFD")) {  // Unicode替换字符，表示无法解码
            score += 500;
        }
        if (text.contains("□")) {
            score += 200;
        }
        if (text.contains("▯")) {
            score += 200;
        }
        // 检测其他常见乱码字符
        if (text.contains("锟斤拷") || text.contains("烫烫烫") || text.contains("屯屯屯")) {
            score += 300;
        }

        return score;
    }

    /**
     * 判断是否为标点符号
     */
    private boolean isPunctuationMark(char c) {
        // 使用Character类的内置方法检测标点符号
        if (Character.getType(c) == Character.DASH_PUNCTUATION
            || Character.getType(c) == Character.START_PUNCTUATION
            || Character.getType(c) == Character.END_PUNCTUATION
            || Character.getType(c) == Character.CONNECTOR_PUNCTUATION
            || Character.getType(c) == Character.OTHER_PUNCTUATION
            || Character.getType(c) == Character.INITIAL_QUOTE_PUNCTUATION
            || Character.getType(c) == Character.FINAL_QUOTE_PUNCTUATION) {
            return true;
        }

        // 额外检查一些可能未被上述方法捕获的中文标点符号
        int[] punctuations = {
            0x3002, // 。
            0xFF0C, // ，
            0xFF01, // ！
            0xFF1F, // ？
            0xFF1B, // ；
            0xFF1A, // ：
            0x201C, // "
            0x201D, // "
            0x2018, // '
            0x2019, // '
            0xFF08, // （
            0xFF09, // ）
            0x3010, // 【
            0x3011, // 】
            0x300A, // 《
            0x300B, // 》
            0x3001, // 、
            0xFF5E, // ～
            0x2026, // …
            0x2014, // —
            0x2015  // ―
        };

        for (int p : punctuations) {
            if (c == p) {
                return true;
            }
        }
        return false;
    }
}