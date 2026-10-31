-- One database, one schema + login per service (database-per-service, logically separated).
-- Passwords are local development defaults; change them for any shared environment.

CREATE EXTENSION IF NOT EXISTS vector;

DO $$
DECLARE
    svc TEXT;
BEGIN
    FOREACH svc IN ARRAY ARRAY['ingestion', 'incident', 'agent', 'knowledge', 'remediation', 'audit']
    LOOP
        EXECUTE format('CREATE ROLE %I LOGIN PASSWORD %L', svc, svc);
        EXECUTE format('CREATE SCHEMA %I AUTHORIZATION %I', svc, svc);
        EXECUTE format('ALTER ROLE %I SET search_path = %I, public', svc, svc);
    END LOOP;
END
$$;
