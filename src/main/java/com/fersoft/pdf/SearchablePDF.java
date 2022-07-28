package com.fersoft.pdf;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.stream.Stream;

public class SearchablePDF {
    private static final Logger logger = LoggerFactory.getLogger(SearchablePDF.class);

    public static void main(String[] args) throws IOException {
        if (args.length < 3) {
            logger.error("Usage:java SearchablePDF <s3-bucket> <input-dir> <output-dir>");
            System.exit(1);
        }
        if (!Files.isDirectory(Paths.get(args[2]))) {
            logger.error("Output directory is not valid {}", args[2]);
            System.exit(1);
        }
        SearchablePDFFromLocalFile localPdf = new SearchablePDFFromLocalFile();
        try (Stream<Path> files = Files.list(Paths.get(args[1]))) {
            files.forEach(p ->
                    {
                        if (p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith("pdf")) {
                            try {
                                localPdf.run(args[0], p, Paths.get(args[2], p.getFileName().toString()));
                            } catch (IOException e) {
                                logger.error("Exception during textract");
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                logger.error("Exception during textract");
                            }
                        }
                    }
            );
        }
    }
}