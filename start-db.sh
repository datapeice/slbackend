#!/bin/bash

# Скрипт для запуска PostgreSQL и MinIO через Podman Compose

echo "Запуск PostgreSQL и MinIO через Podman..."

# Проверка наличия podman-compose
if ! command -v podman-compose &> /dev/null
then
    echo "podman-compose не найден. Устанавливаю..."
    sudo dnf install -y podman-compose || pip3 install podman-compose
fi

# Запуск контейнеров
podman-compose up -d

# Проверка статуса
echo ""
echo "Ожидание запуска сервисов..."
sleep 10

if podman ps | grep -q slbackend-postgres; then
    echo "✓ PostgreSQL успешно запущен"
    echo ""
    echo "Параметры подключения PostgreSQL:"
    echo "  Host: localhost"
    echo "  Port: 5432"
    echo "  Database: slbackend"
    echo "  Username: slbackend_user"
    echo "  Password: slbackend_password"
else
    echo "✗ Ошибка запуска PostgreSQL"
    echo "Проверьте логи: podman logs slbackend-postgres"
fi

echo ""

if podman ps | grep -q slbackend-minio; then
    echo "✓ MinIO успешно запущен"
    echo ""
    echo "Параметры подключения MinIO:"
    echo "  API Endpoint: http://localhost:9000"
    echo "  Console (Web UI): http://localhost:9001"
    echo "  Access Key: minioadmin"
    echo "  Secret Key: minioadmin123"
    echo "  Bucket: slbackend-avatars (создастся автоматически)"
else
    echo "✗ Ошибка запуска MinIO"
    echo "Проверьте логи: podman logs slbackend-minio"
fi

echo ""
echo "Для остановки выполните: podman-compose down"
echo "Для просмотра логов:"
echo "  PostgreSQL: podman logs -f slbackend-postgres"
echo "  MinIO: podman logs -f slbackend-minio"
echo ""

