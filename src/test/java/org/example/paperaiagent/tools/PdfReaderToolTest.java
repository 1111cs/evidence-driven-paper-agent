package org.example.paperaiagent.tools;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PdfReaderToolTest {


    @Test
    void readPdfContent() {
        String fileDir = System.getProperty("user.dir") + "/tmp/pdf/2104.06403v1.pdf";
        PdfReaderTool pdfReaderTool = new PdfReaderTool();
        String s = pdfReaderTool.readPdfContent(fileDir);
        Assertions.assertNotNull(s);
    }
}