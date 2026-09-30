package org.example.paperaiagent.tools;


import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import java.io.File;
import java.io.IOException;

public class PdfReaderTool {

    public String readPdfContent(String filePath) {
        try (PDDocument document = Loader.loadPDF(new File(filePath))) {
            // 检查文档是否加密
            if (document.isEncrypted()) {
                return "错误：PDF文件已加密，无法读取";
            }

            PDFTextStripper stripper = new PDFTextStripper();
            // 设置文本提取范围（可选）
            stripper.setStartPage(1);
            stripper.setEndPage(document.getNumberOfPages());

            return stripper.getText(document);
        } catch (IOException e) {
            return "读取PDF失败：" + e.getMessage();
        }
    }
}
