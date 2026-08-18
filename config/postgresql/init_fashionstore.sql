CREATE USER supabase_admin LOGIN CREATEROLE CREATEDB REPLICATION BYPASSRLS PASSWORD 'postgres';

CREATE USER supabase_auth_admin NOINHERIT CREATEROLE LOGIN NOREPLICATION PASSWORD 'root';

CREATE DATABASE fashionstore;
\c fashionstore

CREATE SCHEMA IF NOT EXISTS auth AUTHORIZATION supabase_auth_admin;
GRANT ALL ON SCHEMA auth TO supabase_auth_admin;
ALTER USER supabase_auth_admin SET search_path = 'auth';

GRANT ALL ON SCHEMA public TO postgres;
