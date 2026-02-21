#!/bin/bash

# Скрипт для тестирования Admin API

BASE_URL="http://localhost:8080"
TOKEN=""

echo "🔐 Тестирование Admin API"
echo "=========================="
echo ""

# Цвета
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

print_test() {
    echo -e "${YELLOW}► $1${NC}"
}

print_success() {
    echo -e "${GREEN}✓ $1${NC}"
}

# Вход как админ
print_test "1. Вход в систему"
LOGIN_RESPONSE=$(curl -s -X POST "$BASE_URL/api/auth/login" \
  -H "Content-Type: application/json" \
  -d '{
    "username": "testplayer",
    "password": "TestPass123!"
  }')

TOKEN=$(echo "$LOGIN_RESPONSE" | jq -r '.token')
echo "Token: $TOKEN"
print_success "Успешный вход"
echo ""

# Получение всех заявок
print_test "2. Получение всех заявок"
curl -s -X GET "$BASE_URL/api/admin/applications" \
  -H "Authorization: Bearer $TOKEN" | jq '.'
print_success "Заявки получены"
echo ""
sleep 1

# Получение PENDING заявок
print_test "3. Получение PENDING заявок"
curl -s -X GET "$BASE_URL/api/admin/applications?status=PENDING" \
  -H "Authorization: Bearer $TOKEN" | jq '.'
print_success "PENDING заявки получены"
echo ""
sleep 1

# Получить ID первой заявки
print_test "4. Получение ID заявки для обновления"
APP_ID=$(curl -s -X GET "$BASE_URL/api/admin/applications" \
  -H "Authorization: Bearer $TOKEN" | jq -r '.[0].id')
echo "Application ID: $APP_ID"
echo ""

# Обновление статуса заявки
print_test "5. Принятие заявки"
curl -s -X PATCH "$BASE_URL/api/admin/applications/$APP_ID/status" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "status": "ACCEPTED",
    "adminComment": "Отличная заявка! Добро пожаловать на сервер!"
  }' | jq '.'
print_success "Заявка принята"
echo ""
sleep 1

# Получение всех пользователей
print_test "6. Получение всех пользователей"
curl -s -X GET "$BASE_URL/api/admin/users" \
  -H "Authorization: Bearer $TOKEN" | jq '.'
print_success "Список пользователей получен"
echo ""

echo "=========================="
echo -e "${GREEN}✓ Admin тесты выполнены!${NC}"
echo ""

