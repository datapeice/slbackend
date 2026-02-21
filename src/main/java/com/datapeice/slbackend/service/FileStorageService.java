package com.datapeice.slbackend.service;

import io.minio.*;
import io.minio.http.Method;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class FileStorageService {

    private static final Logger logger = LoggerFactory.getLogger(FileStorageService.class);

    private final MinioClient minioClient;
    private final String bucketName;
    private final String minioEndpoint;
    private final String minioPublicUrl;
    private final int presignedUrlExpiryHours;

    public FileStorageService(
            MinioClient minioClient,
            @Value("${minio.bucket-name}") String bucketName,
            @Value("${minio.endpoint}") String minioEndpoint,
            @Value("${minio.public-url:}") String minioPublicUrl,
            @Value("${minio.presigned-url-expiry-hours:168}") int presignedUrlExpiryHours) {
        this.minioClient = minioClient;
        this.bucketName = bucketName;
        this.minioEndpoint = minioEndpoint;
        this.minioPublicUrl = (minioPublicUrl != null && !minioPublicUrl.isBlank()) ? minioPublicUrl : null;
        this.presignedUrlExpiryHours = presignedUrlExpiryHours;
        logger.info("FileStorageService initialized: endpoint={}, bucket={}, publicUrl={}, presignedExpiry={}h",
                minioEndpoint, bucketName, this.minioPublicUrl, presignedUrlExpiryHours);
    }

    /**
     * Builds a public URL for a stored object.
     * If minio.public-url is set, uses it as base (virtual-hosted-style for AWS S3).
     * Otherwise falls back to path-style: endpoint/bucket/object.
     */
    private String buildPublicUrl(String filename) {
        if (minioPublicUrl != null) {
            // virtual-hosted-style: https://bucket.s3.region.amazonaws.com/key
            return minioPublicUrl.stripTrailing() + "/" + filename;
        }
        // path-style fallback (local MinIO)
        return minioEndpoint + "/" + bucketName + "/" + filename;
    }

    /**
     * Загружает файл в MinIO и возвращает URL (presigned или публичный)
     */
    public String uploadFile(MultipartFile file, String folder) {
        try {
            // Валидация
            if (file.isEmpty()) {
                throw new IllegalArgumentException("Файл пустой");
            }

            // Проверка типа файла
            String contentType = file.getContentType();
            if (contentType == null || !contentType.startsWith("image/")) {
                throw new IllegalArgumentException("Можно загружать только изображения");
            }

            // Проверка размера (5MB)
            if (file.getSize() > 5 * 1024 * 1024) {
                throw new IllegalArgumentException("Размер файла не должен превышать 5MB");
            }

            // Генерируем уникальное имя файла
            String originalFilename = file.getOriginalFilename();
            String extension = "";
            if (originalFilename != null && originalFilename.contains(".")) {
                extension = originalFilename.substring(originalFilename.lastIndexOf("."));
            }
            String filename = folder + "/" + UUID.randomUUID() + extension;

            // Загружаем файл (без ACL заголовка — Bucketeer блокирует публичные ACL)
            try (InputStream inputStream = file.getInputStream()) {
                minioClient.putObject(
                        PutObjectArgs.builder()
                                .bucket(bucketName)
                                .object(filename)
                                .stream(inputStream, file.getSize(), -1)
                                .contentType(contentType)
                                .build()
                );
            }

            logger.info("Uploaded file to S3/MinIO: bucket={}, key={}", bucketName, filename);
            // Возвращаем object key для сохранения в БД
            return filename;

        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            logger.error("Ошибка при загрузке файла в S3/MinIO: endpoint={}, bucket={}, error={}", minioEndpoint, bucketName, e.getMessage(), e);
            throw new RuntimeException("Ошибка при загрузке файла: " + e.getMessage(), e);
        }
    }

    /**
     * Возвращает URL для объекта: публичный если настроен minio.public-url,
     * иначе — presigned URL с настраиваемым временем жизни.
     */
    public String resolveUrl(String objectKey) {
        if (minioPublicUrl != null) {
            return buildPublicUrl(objectKey);
        }
        // Использовать presigned URL
        try {
            String url = minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(bucketName)
                            .object(objectKey)
                            .expiry(presignedUrlExpiryHours, TimeUnit.HOURS)
                            .build()
            );
            logger.debug("Generated presigned URL for {}: {}", objectKey, url);
            return url;
        } catch (Exception e) {
            logger.error("Ошибка при создании presigned URL для {}: {}", objectKey, e.getMessage(), e);
            throw new RuntimeException("Ошибка при создании presigned URL: " + e.getMessage(), e);
        }
    }

    /**
     * Загружает файл из InputStream в MinIO и возвращает URL
     */
    public String uploadFromStream(InputStream inputStream, long size, String contentType, String folder, String extension) {
        try {
            String filename = folder + "/" + UUID.randomUUID() + extension;
            // Загружаем без ACL заголовка
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(filename)
                            .stream(inputStream, size, -1)
                            .contentType(contentType)
                            .build()
            );
            return filename;
        } catch (Exception e) {
            throw new RuntimeException("Ошибка при загрузке файла из потока: " + e.getMessage(), e);
        }
    }

    /**
     * Удаляет файл из MinIO
     */
    public void deleteFile(String fileUrl) {
        try {
            // Извлекаем путь к файлу из URL
            String objectName = extractObjectNameFromUrl(fileUrl);
            if (objectName != null) {
                minioClient.removeObject(
                        RemoveObjectArgs.builder()
                                .bucket(bucketName)
                                .object(objectName)
                                .build()
                );
            }
        } catch (Exception e) {
            // Логируем, но не бросаем исключение
            System.err.println("Ошибка при удалении файла: " + e.getMessage());
        }
    }

    /**
     * Получает временную подписанную ссылку на файл (если нужно)
     */
    public String getPresignedUrl(String objectName, int expiryMinutes) {
        try {
            return minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(bucketName)
                            .object(objectName)
                            .expiry(expiryMinutes, TimeUnit.MINUTES)
                            .build()
            );
        } catch (Exception e) {
            throw new RuntimeException("Ошибка при получении URL: " + e.getMessage(), e);
        }
    }

    /**
     * Public method to extract object key from a URL (for re-resolving old URLs).
     */
    public String extractObjectKey(String fileUrl) {
        return extractObjectNameFromUrl(fileUrl);
    }

    /**
     * Извлекает имя объекта из полного URL или возвращает значение как есть, если это уже ключ объекта.
     * Handles: object key, path-style URL, virtual-hosted-style URL, presigned URL.
     */
    private String extractObjectNameFromUrl(String fileUrl) {
        if (fileUrl == null) return null;
        // If it doesn't start with http, it's already an object key
        if (!fileUrl.startsWith("http://") && !fileUrl.startsWith("https://")) {
            return fileUrl;
        }
        // Strip query string (presigned URL params)
        String urlWithoutQuery = fileUrl.contains("?") ? fileUrl.substring(0, fileUrl.indexOf("?")) : fileUrl;
        // Try virtual-hosted-style first (publicUrl/key)
        if (minioPublicUrl != null && urlWithoutQuery.startsWith(minioPublicUrl)) {
            String base = minioPublicUrl.endsWith("/") ? minioPublicUrl : minioPublicUrl + "/";
            return urlWithoutQuery.substring(base.length());
        }
        // Fall back to path-style (endpoint/bucket/key)
        if (urlWithoutQuery.contains(bucketName)) {
            int bucketIndex = urlWithoutQuery.indexOf(bucketName);
            return urlWithoutQuery.substring(bucketIndex + bucketName.length() + 1);
        }
        return null;
    }
}

