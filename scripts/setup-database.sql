\set ON_ERROR_STOP on

SELECT format(
    'CREATE ROLE performance_lab LOGIN PASSWORD %L NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION',
    :'lab_password')
WHERE NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'performance_lab')
\gexec

SELECT format('ALTER ROLE performance_lab PASSWORD %L', :'lab_password')
\gexec

SELECT 'CREATE DATABASE performance_lab OWNER performance_lab'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'performance_lab')
\gexec
