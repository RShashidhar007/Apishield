-- One-shot database bootstrap (idempotent). Flyway then owns the schema.
IF DB_ID('apishield') IS NULL CREATE DATABASE apishield;
GO
IF NOT EXISTS (SELECT 1 FROM sys.sql_logins WHERE name = 'apishield_app') CREATE LOGIN apishield_app WITH PASSWORD = '$(APP_PASSWORD)', CHECK_POLICY = OFF;
GO
USE apishield;
GO
IF NOT EXISTS (SELECT 1 FROM sys.database_principals WHERE name = 'apishield_app') CREATE USER apishield_app FOR LOGIN apishield_app;
ALTER ROLE db_owner ADD MEMBER apishield_app;   -- required for Flyway DDL; scoped to the apishield database only
GO
