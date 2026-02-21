#!/bin/bash

echo "🚀 Starting StoryLegends Backend..."
echo ""

# Проверяем, запущен ли PostgreSQL
if ! podman ps | grep -q slbackend-postgres; then
    echo "❌ PostgreSQL не запущен!"
    echo "Запустите: ./start-db.sh"
    exit 1
fi

# Проверяем, свободен ли порт 8080
if lsof -Pi :8080 -sTCP:LISTEN -t >/dev/null 2>&1; then
    echo "⚠️  Порт 8080 занят. Останавливаю процесс..."
    lsof -ti:8080 | xargs kill -9 2>/dev/null
    sleep 2
fi

echo "✅ Все проверки пройдены"
echo ""
echo "📦 Запуск приложения с профилем 'dev'..."
echo "   - reCAPTCHA: ОТКЛЮЧЕНА"
echo "   - Email: ОТКЛЮЧЕН"
echo "   - Auto-verify: ВКЛЮЧЕНО"
echo ""
echo "🌐 Backend будет доступен на: http://localhost:8080"
echo "📝 Для остановки нажмите Ctrl+C"
echo ""
echo "---"
echo ""

# Запускаем приложение
./gradlew bootRun --args='--spring.profiles.active=dev'

