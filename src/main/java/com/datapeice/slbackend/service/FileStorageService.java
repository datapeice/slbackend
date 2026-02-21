package com.datapeice.slbackend.service;

import io.minio.*;
import io.minio.http.Method;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class FileStorageService {

    private final MinioClient minioClient;
    private final String bucketName;
    private final String minioEndpoint;

    public FileStorageService(
            MinioClient minioClient,
            @Value("${minio.bucket-name}") String bucketName,
            @Value("${minio.endpoint}") String minioEndpoint) {
        this.minioClient = minioClient;
        this.bucketName = bucketName;
        this.minioEndpoint = minioEndpoint;
    }

    /**
     * Загружает файл в MinIO и возвращает URL
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

            // Загружаем файл
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

            // Возвращаем публичный URL
            return minioEndpoint + "/" + bucketName + "/" + filename;

        } catch (Exception e) {
            throw new RuntimeException("Ошибка при загрузке файла: " + e.getMessage(), e);
        }
    }

    /**
     * Загружает файл из InputStream в MinIO и возвращает публичный URL
     */
    public String uploadFromStream(InputStream inputStream, long size, String contentType, String folder, String extension) {
        try {
            String filename = folder + "/" + UUID.randomUUID() + extension;
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(filename)
                            .stream(inputStream, size, -1)
                            .contentType(contentType)
                            .build()
            );
            return minioEndpoint + "/" + bucketName + "/" + filename;
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
     * Извлекает имя объекта из полного URL
     */
    private String extractObjectNameFromUrl(String fileUrl) {
        if (fileUrl == null || !fileUrl.contains(bucketName)) {
            return null;
        }
        int bucketIndex = fileUrl.indexOf(bucketName);
        return fileUrl.substring(bucketIndex + bucketName.length() + 1);
    }
}

