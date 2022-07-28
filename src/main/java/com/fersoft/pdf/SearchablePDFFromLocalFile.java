package com.fersoft.pdf;

import com.amazon.textract.pdf.ImageType;
import com.amazon.textract.pdf.PDFDocument;
import com.amazon.textract.pdf.TextLine;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.textract.AmazonTextract;
import com.amazonaws.services.textract.AmazonTextractClientBuilder;
import com.amazonaws.services.textract.model.*;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class SearchablePDFFromLocalFile {
    private static final Logger logger = LoggerFactory.getLogger(SearchablePDFFromLocalFile.class);

    public void run(String bucket, Path inputFile, Path outputFile) throws IOException, InterruptedException {
        logger.info("Generating searchable pdf from {} to directory {} , bucket {}", inputFile, outputFile, bucket);
        uploadFileToS3(bucket, inputFile);
        PDFDocument pdfDocument = addTextToPDF(bucket, inputFile);
        savePdfToFile(pdfDocument, outputFile);
        deleteFileFromS3(bucket, inputFile);
        logger.info("Generated searchable pdf: {} ", outputFile);
    }

    private void deleteFileFromS3(String bucket, Path inputFile) {
        AmazonS3 s3client = AmazonS3ClientBuilder.defaultClient();
        s3client.deleteObject(bucket, inputFile.getFileName().toString());
    }

    private void savePdfToFile(PDFDocument pdfDocument, Path outputDocument) throws IOException {
        //Save PDF to local disk
        try (OutputStream outputStream = new FileOutputStream(outputDocument.toFile())) {
            pdfDocument.save(outputStream);
            pdfDocument.close();
        }
    }

    private PDFDocument addTextToPDF(String bucket, Path inputFile) throws InterruptedException, IOException {
        List<ArrayList<TextLine>> linesInPages = extractText(bucket, inputFile.getFileName().toString());
        //Create new PDF document
        PDFDocument pdfDocument = new PDFDocument();

        //For each page add text layer and image in the pdf document
        PDDocument inputDocument = PDDocument.load(inputFile.toFile());
        PDFRenderer pdfRenderer = new PDFRenderer(inputDocument);

        for (int page = 0; page < inputDocument.getNumberOfPages(); ++page) {
            BufferedImage image = pdfRenderer.renderImageWithDPI(page, 300, org.apache.pdfbox.rendering.ImageType.RGB);
            pdfDocument.addPage(image, ImageType.JPEG, linesInPages.get(page));
            logger.info("Processed page index: {}", page);
        }

        //Save PDF to stream
        inputDocument.close();
        return pdfDocument;
    }

    private List<ArrayList<TextLine>> extractText(String bucketName, String inputFile) throws InterruptedException {
        AmazonTextract client = AmazonTextractClientBuilder.defaultClient();

        String jobId = executeTextractJob(bucketName, inputFile, client);

        List<ArrayList<TextLine>> pages = new ArrayList<>();
        ArrayList<TextLine> page = new ArrayList<>();

        String paginationToken = null;
        do {
            GetDocumentTextDetectionRequest documentTextDetectionRequest = new GetDocumentTextDetectionRequest()
                    .withJobId(jobId)
                    .withMaxResults(1000)
                    .withNextToken(paginationToken);
            GetDocumentTextDetectionResult response = client.getDocumentTextDetection(documentTextDetectionRequest);

            //Show blocks information
            List<Block> blocks = response.getBlocks();
            for (Block block : blocks) {
                if (block.getBlockType().equals("PAGE")) {
                    page = new ArrayList<>();
                    pages.add(page);
                } else if (block.getBlockType().equals("LINE")) {
                    BoundingBox boundingBox = block.getGeometry().getBoundingBox();
                    page.add(new TextLine(boundingBox.getLeft(),
                            boundingBox.getTop(),
                            boundingBox.getWidth(),
                            boundingBox.getHeight(),
                            block.getText()));
                }
            }
            paginationToken = response.getNextToken();
        } while (paginationToken != null);

        return pages;
    }

    private String executeTextractJob(String bucketName, String inputFile, AmazonTextract client) throws InterruptedException {
        StartDocumentTextDetectionRequest req = new StartDocumentTextDetectionRequest()
                .withDocumentLocation(new DocumentLocation()
                        .withS3Object(new S3Object()
                                .withBucket(bucketName)
                                .withName(inputFile)))
                .withJobTag("DetectingText");

        StartDocumentTextDetectionResult startDocumentTextDetectionResult = client.startDocumentTextDetection(req);
        String jobId = startDocumentTextDetectionResult.getJobId();
        logger.info("Text detection job started with Id: {} ", jobId);
        waitForJobCompletion(client, jobId);
        return jobId;
    }

    private void waitForJobCompletion(AmazonTextract client, String jobId) throws InterruptedException {
        GetDocumentTextDetectionResult response;
        do {
            logger.info("Waiting for job {} to complete...", jobId);
            TimeUnit.SECONDS.sleep(10);
            GetDocumentTextDetectionRequest documentTextDetectionRequest = new GetDocumentTextDetectionRequest()
                    .withJobId(jobId)
                    .withMaxResults(1);

            response = client.getDocumentTextDetection(documentTextDetectionRequest);
        } while ("IN_PROGRESS".equals(response.getJobStatus()));
        logger.info("Job {} completed", jobId);
    }

    private void uploadFileToS3(String bucketName, Path file) throws IOException {
        byte[] bytes = Files.readAllBytes(file);
        AmazonS3 s3client = AmazonS3ClientBuilder.defaultClient();
        if (!s3client.doesBucketExistV2(bucketName)) {
            s3client.createBucket(bucketName);
        }
        ByteArrayInputStream baInputStream = new ByteArrayInputStream(bytes);
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(bytes.length);
        metadata.setContentType("application/pdf");
        PutObjectRequest putRequest = new PutObjectRequest(bucketName, file.getFileName().toString(), baInputStream, metadata);
        s3client.putObject(putRequest);
    }

}
