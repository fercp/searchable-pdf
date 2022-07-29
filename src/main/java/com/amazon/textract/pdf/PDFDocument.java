package com.amazon.textract.pdf;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.graphics.image.JPEGFactory;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.pdmodel.graphics.state.RenderingMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


public class PDFDocument {
    private static final Logger logger = LoggerFactory.getLogger(PDFDocument.class);

    private final PDDocument document;
    private final List<PDFont> fonts;

    public PDFDocument() throws IOException {
        this.document = new PDDocument();
        InputStream notoSansRegularResource = getClass().getClassLoader().getResourceAsStream("NotoSans-Regular.ttf");
        InputStream notoSansCjkRegularResource = getClass().getClassLoader().getResourceAsStream("NotoSansCJKtc-Regular.ttf");
        PDType0Font notoSansRegular = PDType0Font.load(document, notoSansRegularResource);
        PDType0Font notoSansCjkRegular = PDType0Font.load(document, notoSansCjkRegularResource);
        fonts = Arrays.asList(notoSansRegular, notoSansCjkRegular);
    }

    public void addPage(BufferedImage image, ImageType imageType, List<TextLine> lines) throws IOException {

        float width = image.getWidth();
        float height = image.getHeight();

        PDRectangle box = new PDRectangle(width, height);
        PDPage page = new PDPage(box);
        page.setMediaBox(box);
        this.document.addPage(page);

        PDImageXObject pdImage;

        if (imageType == ImageType.JPEG) {
            pdImage = JPEGFactory.createFromImage(this.document, image);
        } else {
            pdImage = LosslessFactory.createFromImage(this.document, image);
        }

        PDPageContentStream contentStream = new PDPageContentStream(document, page);

        contentStream.drawImage(pdImage, 0, 0);

        contentStream.setRenderingMode(RenderingMode.NEITHER);


        for (TextLine cline : lines) {
            List<TextWithFont> fontifiedContent = fontify(fonts, cline.text);

            for (TextWithFont textWithFont : fontifiedContent) {
                FontInfo fontInfo = calculateFontSize(textWithFont.font, cline.text, (float) cline.width * width);
                contentStream.beginText();
                contentStream.setFont(textWithFont.font, fontInfo.fontSize);
                contentStream.newLineAtOffset((float) cline.left * width, (float) (height - height * cline.top - fontInfo.textHeight));
                contentStream.showText(cline.text);
                contentStream.endText();
            }

        }

        contentStream.close();
    }

    public void save(OutputStream os) throws IOException {
        this.document.save(os);
    }

    public void close() throws IOException {
        this.document.close();
    }

    private List<TextWithFont> fontify(List<PDFont> fonts, String text) throws IOException {
        List<TextWithFont> result = new ArrayList<>();
        if (text.length() > 0) {
            PDFont currentFont = null;
            int start = 0;
            int codeChars;
            for (int i = 0; i < text.length(); i += codeChars) {
                int codePoint = text.codePointAt(i);
                codeChars = Character.charCount(codePoint);
                String codePointString = text.substring(i, i + codeChars);
                boolean canEncode = false;
                for (PDFont font : fonts) {
                    try {
                        font.encode(codePointString);
                        canEncode = true;
                        if (font != currentFont) {
                            if (currentFont != null) {
                                result.add(new TextWithFont(text.substring(start, i), currentFont));
                            }
                            currentFont = font;
                            start = i;
                        }
                        break;
                    } catch (Exception ioe) {
                        logger.info("Skipping font {}", font.getFontDescriptor().getFontName());
                    }
                }
                if (!canEncode) {
                    throw new IOException("Cannot encode '" + codePointString + "'.");
                }
            }
            result.add(new TextWithFont(text.substring(start), currentFont));
        }
        return result;
    }


    private FontInfo calculateFontSize(PDFont font, String text, float bbWidth) throws IOException {
        int fontSize = 17;
        float textWidth = font.getStringWidth(text) / 1000 * fontSize;

        if (textWidth > bbWidth) {
            while (textWidth > bbWidth) {
                fontSize -= 1;
                textWidth = font.getStringWidth(text) / 1000 * fontSize;
            }
        } else if (textWidth < bbWidth) {
            while (textWidth < bbWidth) {
                fontSize += 1;
                textWidth = font.getStringWidth(text) / 1000 * fontSize;
            }
        }

        FontInfo fi = new FontInfo();
        fi.fontSize = fontSize;
        fi.textHeight = PDType1Font.COURIER.getFontDescriptor().getFontBoundingBox().getHeight() / 1000 * fontSize;
        fi.textWidth = textWidth;

        return fi;
    }

    record TextWithFont(String text, PDFont font) {
    }
}
