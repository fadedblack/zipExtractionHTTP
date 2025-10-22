package com.http.request;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.GeneratePresignedUrlRequest;
import com.http.request.models.CentralDirectoryInfo;
import com.http.request.utility.ZipExtraction;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Date;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@Slf4j
public class RequestApplication {

    public static void main(String[] args) {
        SpringApplication.run(RequestApplication.class, args);

        String bucketName = "java-http";
        String objectKey = "agreements.zip";

        BasicAWSCredentials awsCredentials = new BasicAWSCredentials("test", "test");
        AmazonS3 amazonS3 = AmazonS3ClientBuilder.standard()
                .withEndpointConfiguration(
                        new AwsClientBuilder.EndpointConfiguration("http://localhost:4566", "ap-south-1"))
                .withCredentials(new AWSStaticCredentialsProvider(awsCredentials))
                .withPathStyleAccessEnabled(true)
                .build();

        try {
            Date expiration = new Date();
            long expTimeMillis = expiration.getTime();
            expTimeMillis += 1000 * 60 * 5; // 5 minutes
            expiration.setTime(expTimeMillis);

            GeneratePresignedUrlRequest generatePresignedUrlRequest = new GeneratePresignedUrlRequest(
                            bucketName, objectKey)
                    .withMethod(com.amazonaws.HttpMethod.GET)
                    .withExpiration(expiration);

            URL presignedUrl = amazonS3.generatePresignedUrl(generatePresignedUrlRequest);
            log.info("Generated presigned URL: {}", presignedUrl);
            ZipExtraction zipExtraction = new ZipExtraction(presignedUrl.toString());
            List<CentralDirectoryInfo> centralDirectoryContents = zipExtraction.getCentralDirectoryContents();
            List<List<Byte>> listStream = zipExtraction.getAllFiles(centralDirectoryContents);
            //			log.info("Central Directory Contents: {}", centralDirectoryContents);
            //			log.info("List Stream: {}", listStream);

            // For checking output files
            Path outputDir = Paths.get("output");

//            for (int i = 0; i < centralDirectoryContents.size(); i++) {
//                CentralDirectoryInfo entry = centralDirectoryContents.get(i);
//                List<Byte> fileBytes = listStream.get(i);
//                if (entry.getFileName() == null || entry.getFileName().isEmpty()) {
//                    log.warn("Skipping entry with empty file name at index {}", i);
//                    continue;
//                }
//                java.nio.file.Path filePath = outputDir.resolve(entry.getFileName());
//                byte[] bytes = new byte[fileBytes.size()];
//                for (int j = 0; j < fileBytes.size(); j++) {
//                    bytes[j] = fileBytes.get(j);
//                }
//                java.nio.file.Files.write(filePath, bytes);
//                log.info("Wrote file: {} ({} bytes)", filePath, bytes.length);
//            }

                        String filename = "frieren_wallpaper.jpg";
                        List<Byte> fileBytes = zipExtraction.getFile(centralDirectoryContents, filename);
                        Path filePath = outputDir.resolve(filename);
                        byte[] bytes = new byte[fileBytes.size()];
                        for (int j = 0; j < fileBytes.size(); j++) {
                            bytes[j] = fileBytes.get(j);
                        }
                        Files.write(filePath, bytes);
                        log.info("Wrote file: {} ({} bytes)", filePath, bytes.length);

        } catch (Exception e) {
            log.error("Error creating InputStream from presigned URL: " + e.getMessage());
        }
    }
}
