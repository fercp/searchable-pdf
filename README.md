# Adding OCR to PDFs using AMAZONTEXTRACT

Add OCR into files in the given directory and creates files in the output directory.
Copies pdf files in the source directory to s3 bucket then executes textract job.


### Building project

```shell
    mvn clean package
```

### Usage

- s3-bucket-name : bucket into which input pdf files copied for batch execution
- source-directory : directory of source pdf files
- output-directory : destination directory of OCRed pdf files


```shell
   java -jar searchable-pdf-1.0.jar s3-bucket-name source-directory output-directory resolution
```