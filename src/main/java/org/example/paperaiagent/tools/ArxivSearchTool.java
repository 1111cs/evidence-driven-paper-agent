package org.example.paperaiagent.tools;


import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.springframework.web.client.RestTemplate;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
public class ArxivSearchTool {
    private final RestTemplate restTemplate;

    public ArxivSearchTool() {
        this.restTemplate = createRestTemplateWithTimeout();
    }

    // 创建带超时控制的RestTemplate
    private RestTemplate createRestTemplateWithTimeout() {
        ClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        ((SimpleClientHttpRequestFactory) factory).setConnectTimeout(5000);  // 5秒连接超时
        ((SimpleClientHttpRequestFactory) factory).setReadTimeout(10000);     // 10秒读取超时
        return new RestTemplate(factory);
    }

    public List<ArxivPaperResult> searchPapers(String query) {
        final int maxRetries = 3;
        int retryCount = 0;

        while (retryCount < maxRetries) {
            try {
                String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
                String apiUrl = String.format(
                        "https://export.arxiv.org/api/query?search_query=all:%s&start=0&max_results=5",
                        encodedQuery
                );

                // 使用ResponseEntity确保获取完整响应
                ResponseEntity<String> responseEntity = restTemplate.getForEntity(apiUrl, String.class);

                if (responseEntity.getStatusCode() == HttpStatus.OK && responseEntity.getBody() != null) {
                    return parseXmlResponse(responseEntity.getBody());
                } else {
                    throw new RuntimeException("arXiv API returned non-OK status: " + responseEntity.getStatusCode());
                }
            } catch (Exception e) {
                retryCount++;
                if (retryCount < maxRetries) {
                    try {
                        // 指数退避重试策略
                        long waitTime = (long) Math.pow(2, retryCount) * 500;
                        TimeUnit.MILLISECONDS.sleep(waitTime);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                } else {
                    throw new RuntimeException("arXiv搜索失败，重试" + maxRetries + "次后仍不可用: " + e.getMessage(), e);
                }
            }
        }
        return Collections.emptyList();
    }

    private List<ArxivPaperResult> parseXmlResponse(String xml) throws Exception {
        List<ArxivPaperResult> papers = new ArrayList<>();

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(new InputSource(new StringReader(xml)));

        NodeList entries = doc.getElementsByTagName("entry");
        for (int i = 0; i < entries.getLength(); i++) {
            Element entry = (Element) entries.item(i);

            String id = getElementText(entry, "id");
            String title = getElementText(entry, "title").replace("\n", " ").trim();
            String summary = getElementText(entry, "summary").replace("\n", " ").trim();
            String published = getElementText(entry, "published");
            String updated = getElementText(entry, "updated");

            // 提取作者列表
            List<String> authors = new ArrayList<>();
            NodeList authorNodes = entry.getElementsByTagName("author");
            for (int j = 0; j < authorNodes.getLength(); j++) {
                Element author = (Element) authorNodes.item(j);
                authors.add(getElementText(author, "name"));
            }

            // 提取PDF链接
            String pdfUrl = "";
            NodeList links = entry.getElementsByTagName("link");
            for (int j = 0; j < links.getLength(); j++) {
                Element link = (Element) links.item(j);
                if ("application/pdf".equals(link.getAttribute("type"))) {
                    pdfUrl = link.getAttribute("href");
                    break;
                }
            }

            // 创建论文对象
            papers.add(new ArxivPaperResult(id, title, summary, published, updated, authors, pdfUrl));
        }

        return papers;
    }

    private String getElementText(Element parent, String tagName) {
        NodeList nodes = parent.getElementsByTagName(tagName);
        if (nodes.getLength() > 0) {
            return nodes.item(0).getTextContent();
        }
        return "";
    }

    // 论文数据模型
    public static class ArxivPaperResult {
        private final String id;
        private final String title;
        private final String summary;
        private final String publishedDate;
        private final String updatedDate;
        private final List<String> authors;
        private final String pdfUrl;

        public ArxivPaperResult(String id, String title, String summary, String publishedDate,
                          String updatedDate, List<String> authors, String pdfUrl) {
            this.id = id;
            this.title = title;
            this.summary = summary;
            this.publishedDate = publishedDate;
            this.updatedDate = updatedDate;
            this.authors = authors;
            this.pdfUrl = pdfUrl;
        }

        // Getters
        public String getId() { return id; }
        public String getTitle() { return title; }
        public String getSummary() { return summary; }
        public String getPublishedDate() { return publishedDate; }
        public String getUpdatedDate() { return updatedDate; }
        public List<String> getAuthors() { return authors; }
        public String getPdfUrl() { return pdfUrl; }

        @Override
        public String toString() {
            return String.format(
                "Title: %s\n" +
                "Authors: %s\n" +
                "Published: %s | Updated: %s\n" +
                "Summary: %s...\n" +
                "PDF: %s\n" +
                "ID: %s\n",
                title,
                String.join(", ", authors),
                publishedDate.substring(0, 10),
                updatedDate.substring(0, 10),
                summary.substring(0, Math.min(150, summary.length())),
                pdfUrl,
                id
            );
        }
    }
}
