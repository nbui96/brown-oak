package com.apani.moodieapigateway.service

import com.apani.moodieapigateway.model.rest.ModelMetadata
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import software.amazon.awssdk.services.s3.S3Client
import org.springframework.stereotype.Service
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider
import software.amazon.awssdk.core.sync.ResponseTransformer
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import java.io.*
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

@Service
class S3Service(
    @Value("\${aws.bucket}") private val bucket: String,
    @Value("\${aws.region}") private val region: String,
    @Value("\${aws.downloadDir}") private val downloadDir: String,
) {

    private val log = LoggerFactory.getLogger(S3Service::class.java)
    private val objectMapper = jacksonObjectMapper()

    /**
     * Initialize models when application starts
     * This will run once the application is fully initialized
     */
    @EventListener(ApplicationReadyEvent::class)
    fun initializeModel() {
        log.info("Application started - initializing recommendation model...")
        try {
            downloadLatestModel()
            log.info("Model initialization completed successfully")
        } catch (e: Exception) {
            log.error("Error initializing model on startup: ${e.message}", e)
        }
    }

    /**
     * Downloads and extracts the latest movie recommendation model from S3.
     *
     * The process follows these steps:
     * 1. Download latest metadata from S3 to identify the current version
     * 2. Compare with locally installed version (if any)
     * 3. Download and extract model files if:
     *    - No local model exists
     *    - Local model version differs from latest
     *    - Force download is requested
     * 4. Clean up temporary files automatically
     *
     * This method handles the entire lifecycle of model management, ensuring
     * the application always has access to recommendation data.
     */
    fun downloadLatestModel() {
        val s3Client: S3Client = S3Client.builder()
            .region(Region.of(region))
            .credentialsProvider(DefaultCredentialsProvider.create())
            .build()

        val metadataKey = "models/metadata/latest.json"
        val tmpPath = Paths.get(downloadDir, "temp", "latest.json")
        Files.createDirectories(tmpPath.parent)
        val metadataTmpFile = tmpPath.toFile()

        log.info("Downloading metadata from s3://$bucket//$metadataKey")
        s3Client.getObject(
            GetObjectRequest.builder()
                .bucket(bucket)
                .key(metadataKey)
                .build(),
            ResponseTransformer.toFile(metadataTmpFile)
        )

        // Parse the metadata to get the latest model version
        val metadata = objectMapper.readValue<ModelMetadata>(metadataTmpFile)
        val latestVersion = metadata.version
        log.info("Latest model version is: $latestVersion")

        val modelDir = Paths.get(downloadDir, "latest-models")
        val currentMetadataFile = modelDir.resolve("metadata.json")

        if (Files.exists(currentMetadataFile)) {
            val currentMetadata = objectMapper.readValue<ModelMetadata>(currentMetadataFile.toFile())

            if (currentMetadata.version == latestVersion) {
                log.info("Current model is latest, No need to update...")
                metadataTmpFile.parentFile.deleteRecursively()
                return
            }
        }

        Files.createDirectories(modelDir)

        val metadataFile = modelDir.resolve("metadata.json")
        Files.copy(metadataTmpFile.toPath(), metadataFile, StandardCopyOption.REPLACE_EXISTING)
        metadataTmpFile.parentFile.deleteRecursively()

        val modelKey = "models/movie_recommender_model_$latestVersion.zip"
        val modelFile = modelDir.resolve("model.zip")

        log.info("Downloading model from s3://$bucket/$modelKey to $modelFile")
        s3Client.getObject(
            GetObjectRequest.builder()
                .bucket(bucket)
                .key(modelKey)
                .build(),
            ResponseTransformer.toFile(modelFile.toFile())
        )

        // Extract the zip file
        try {
            log.info("Extracting model file using Java's ZIP tools")
            extractZip(modelFile.toFile(), modelDir.toFile())
            log.info("Extraction completed successfully")
        } catch (e: Exception) {
            log.error("Error during ZIP extraction: ${e.message}", e)
            throw e
        }

        // Delete the zip file to save space (optional)
        Files.deleteIfExists(modelFile)

        log.info("Successfully downloaded and extracted model version $latestVersion to $modelDir")
    }

    /**
     * Extract a ZIP file using Java's built-in ZIP capabilities.
     * This doesn't rely on external commands being installed.
     */
    private fun extractZip(zipFile: File, destDir: File) {
        if (!destDir.exists()) destDir.mkdirs()

        ZipInputStream(BufferedInputStream(FileInputStream(zipFile))).use { zis ->
            val buffer = ByteArray(1024)
            var entry = zis.nextEntry

            while (entry != null) {
                val filePath = File(destDir, entry.name)

                if (entry.isDirectory) {
                    filePath.mkdirs()
                } else {
                    filePath.parentFile.mkdirs()
                    FileOutputStream(filePath).use { fos ->
                        var len: Int
                        while (zis.read(buffer).also { len = it } > 0) {
                            fos.write(buffer, 0, len)
                        }
                    }
                }

                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }
}


