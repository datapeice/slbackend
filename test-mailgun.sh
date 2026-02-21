#!/bin/bash

# Test Mailgun Email Sending
# This script tests if Mailgun is configured correctly

echo "🧪 Testing Mailgun Configuration..."
echo ""

# Read .env file
if [ -f .env ]; then
    export $(cat .env | grep -v '^#' | xargs)
fi

API_KEY="${MAILGUN_API_KEY:-}"
DOMAIN="${MAILGUN_DOMAIN:-storylegends.xyz}"
FROM_EMAIL="${MAILGUN_FROM_EMAIL:-noreply@storylegends.xyz}"
MAILGUN_API="https://api.eu.mailgun.net"

echo "📧 Configuration:"
echo "   API Key: ${API_KEY:0:20}..."
echo "   Domain: $DOMAIN"
echo "   From Email: $FROM_EMAIL"
echo ""

# Check if API key is set
if [ "$API_KEY" = "your-mailgun-api-key" ] || [ -z "$API_KEY" ]; then
    echo "❌ ERROR: Mailgun API key not configured!"
    echo "   Please set MAILGUN_API_KEY in .env file"
    exit 1
fi

# Test email address
TEST_EMAIL="${1:-elijah@datapeice.me}"

echo "🚀 Sending test email to: $TEST_EMAIL"
echo ""

# Send test email using Mailgun API
RESPONSE=$(curl -s --user "api:${API_KEY}" \
    ${MAILGUN_API}/v3/${DOMAIN}/messages \
    -F from="StoryLegends <${FROM_EMAIL}>" \
    -F to="${TEST_EMAIL}" \
    -F subject="Test Email from StoryLegends Backend" \
    -F text="This is a test email to verify Mailgun configuration. If you received this, everything is working!" \
    -F html="<h1>Test Successful! ✅</h1><p>Your Mailgun configuration is working correctly.</p>")

echo "📬 Response from Mailgun:"
echo "$RESPONSE" | jq '.' 2>/dev/null || echo "$RESPONSE"
echo ""

# Check if successful
if echo "$RESPONSE" | grep -q "Queued"; then
    echo "✅ SUCCESS! Email sent successfully!"
    echo "   Check your inbox at: $TEST_EMAIL"
else
    echo "❌ FAILED! Email was not sent."
    echo ""
    echo "💡 Common issues:"
    echo "   1. Invalid API key"
    echo "   2. Domain not verified in Mailgun"
    echo "   3. Using wrong Mailgun API endpoint (US vs EU)"
    echo "   4. Sandbox domain - need to add authorized recipients"
    echo ""
    echo "🔗 Check your Mailgun dashboard:"
    echo "   https://app.mailgun.com/mg/dashboard"
fi

