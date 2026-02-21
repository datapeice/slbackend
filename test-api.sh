#!/bin/bash

# Скрипт для тестирования API через curl

BASE_URL="http://localhost:8080"
TOKEN=""

echo "🚀 Тестирование Minecraft Server Backend API"
echo "=============================================="
echo ""

# Цвета для вывода
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Функция для красивого вывода
print_test() {
    echo -e "${YELLOW}► $1${NC}"
}

print_success() {
    echo -e "${GREEN}✓ $1${NC}"
}

print_error() {
    echo -e "${RED}✗ $1${NC}"
}

# 1. Регистрация пользователя
print_test "1. Регистрация нового пользователя"
REGISTER_RESPONSE=$(curl -s -X POST "$BASE_URL/api/auth/register" \
  -H "Content-Type: application/json" \
  -d '{
    "username": "testplayer",
    "password": "TestPass123!",
    "email": "test@example.com",
    "discordNickname": "testplayer#1234",
    "minecraftNickname": "TestPlayer"
  }')

echo "$REGISTER_RESPONSE" | jq '.'

if echo "$REGISTER_RESPONSE" | jq -e '.token' > /dev/null 2>&1; then
    TOKEN=$(echo "$REGISTER_RESPONSE" | jq -r '.token')
    print_success "Регистрация успешна! Токен получен."
else
    print_error "Ошибка регистрации"
fi

echo ""
sleep 1

# 2. Получение профиля
print_test "2. Получение профиля пользователя"
curl -s -X GET "$BASE_URL/api/users/me" \
  -H "Authorization: Bearer $TOKEN" | jq '.'
print_success "Профиль получен"
echo ""
sleep 1

# 3. Создание заявки
print_test "3. Создание заявки на вступление"
curl -s -X POST "$BASE_URL/api/applications" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "firstName": "Иван",
    "lastName": "Тестовый",
    "whyUs": "Хочу играть на вашем сервере, потому что он лучший!",
    "source": "Узнал от друга",
    "makeContent": false,
    "additionalInfo": "Играю в Minecraft уже 5 лет",
    "selfRating": 8
  }' | jq '.'
print_success "Заявка создана"
echo ""
sleep 1

# 4. Получение своей заявки
print_test "4. Получение статуса своей заявки"
curl -s -X GET "$BASE_URL/api/applications/my" \
  -H "Authorization: Bearer $TOKEN" | jq '.'
print_success "Статус заявки получен"
echo ""
sleep 1

# 5. Обновление профиля
print_test "5. Обновление профиля"
curl -s -X PATCH "$BASE_URL/api/users/me" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "avatarUrl": "https://i.pravatar.cc/150?img=12"
  }' | jq '.'
print_success "Профиль обновлен"
echo ""
sleep 1

# 6. Получение списка всех пользователей
print_test "6. Получение списка всех пользователей"
curl -s -X GET "$BASE_URL/api/users" \
  -H "Authorization: Bearer $TOKEN" | jq '.'
print_success "Список пользователей получен"
echo ""

echo "=============================================="
echo -e "${GREEN}✓ Все тесты выполнены!${NC}"
echo ""
echo "Для тестирования админ функций:"
echo "1. Подключитесь к БД: podman exec -it slbackend-postgres psql -U slbackend_user -d slbackend"
echo "2. Выполните: UPDATE users SET role = 'ROLE_ADMIN' WHERE username = 'testplayer';"
echo "3. Запустите: ./test-admin-api.sh"
echo ""

