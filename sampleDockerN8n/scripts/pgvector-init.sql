-- psql variables are set by the wrapper script via -v
--   -v username=... -v userpass=... -v dbname=...

-- 1) Create login role if missing (no DO block; use \gexec)
SELECT format('CREATE ROLE %I LOGIN PASSWORD %L', :'username', :'userpass')
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = :'username')\gexec

-- 2) Create database if missing (owner = role)
SELECT format('CREATE DATABASE %I OWNER %I', :'dbname', :'username')
WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = :'dbname')\gexec

-- 3) Connect to the (new or existing) database
\connect :dbname

-- 4) Ensure owner has DB privileges explicitly
GRANT ALL PRIVILEGES ON DATABASE :dbname TO :"username";

-- 5) Enable pgvector extension (idempotent)
CREATE EXTENSION IF NOT EXISTS vector;

-- 6) Default privileges for future objects in public
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON TABLES    TO :"username";
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON SEQUENCES TO :"username";
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON FUNCTIONS TO :"username";

-- 7) Seed tables (idempotent)
CREATE TABLE IF NOT EXISTS embeddings (
  id SERIAL PRIMARY KEY,
  embedding vector,
  text text,
  created_at timestamptz DEFAULT now()
);

CREATE TABLE IF NOT EXISTS document_metadata (
    id TEXT PRIMARY KEY,
    title TEXT,
    created_at TIMESTAMP DEFAULT NOW(),
    schema TEXT
);

CREATE TABLE IF NOT EXISTS document_rows (
    id SERIAL PRIMARY KEY,
    dataset_id TEXT REFERENCES document_metadata(id),
    row_data JSONB
);
