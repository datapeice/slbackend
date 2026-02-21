#!/bin/bash

# Ensure the container is running
if [ ! "$(podman ps -q -f name=slbackend-postgres)" ]; then
    if [ "$(podman ps -aq -f status=exited -f name=slbackend-postgres)" ]; then
        # cleanup
        echo "Starting postgres container..."
        podman start slbackend-postgres
    else
        echo "Postgres container not found or not running. Please run './start-db.sh'"
        exit 1
    fi
fi

# Function to execute SQL
exec_sql() {
    echo "Executing: $1"
    podman exec -i slbackend-postgres psql -U slbackend_user -d slbackend -c "$1"
}

echo "Applying database migrations..."

# Add bio column if not exists
exec_sql "ALTER TABLE users ADD COLUMN IF NOT EXISTS bio TEXT;"

# Add is_player column if not exists - set default to false for existing users
exec_sql "ALTER TABLE users ADD COLUMN IF NOT EXISTS is_player BOOLEAN DEFAULT FALSE NOT NULL;"

# Add reset password fields
exec_sql "ALTER TABLE users ADD COLUMN IF NOT EXISTS reset_password_token VARCHAR(255);"
exec_sql "ALTER TABLE users ADD COLUMN IF NOT EXISTS reset_password_token_expiry BIGINT;"

echo "Migrations applied successfully."

