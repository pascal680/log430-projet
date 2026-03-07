-- CanBankX – Database initialisation
-- Runs automatically when the MySQL container first starts.
-- Each microservice owns its own isolated schema.

CREATE DATABASE IF NOT EXISTS db_identity CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS db_account  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS db_payment  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- Ensure the application user exists (MYSQL_USER env var creates it, but this
-- acts as a safety net in case it was not set or a version change altered the order).
CREATE USER IF NOT EXISTS 'projet'@'%' IDENTIFIED BY 'projet';
-- Re-apply password in case the user was already created by MYSQL_USER with no privileges
ALTER USER 'projet'@'%' IDENTIFIED BY 'projet';

-- Grant the application user full access to each schema.
-- The username 'projet' matches the DB_USER default in application.yaml files.
GRANT ALL PRIVILEGES ON db_identity.* TO 'projet'@'%';
GRANT ALL PRIVILEGES ON db_account.*  TO 'projet'@'%';
GRANT ALL PRIVILEGES ON db_payment.*  TO 'projet'@'%';

FLUSH PRIVILEGES;
