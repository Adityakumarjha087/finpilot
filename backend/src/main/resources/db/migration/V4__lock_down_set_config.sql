-- Custom settings such as finpilot.user_id can be changed by any role through set_config().
-- Revoke that function from PUBLIC so a query running as the read-only assistant role can never re-bind the
-- user id the nl_* views filter on (the backend binds it with a SET LOCAL statement, which the SQL validator
-- never lets through). Needs owner/superuser rights on pg_catalog; on managed databases without them the
-- migration logs a notice and the SQL validator remains the control for this vector.
DO $$
BEGIN
    REVOKE EXECUTE ON FUNCTION pg_catalog.set_config(text, text, boolean) FROM PUBLIC;
    EXECUTE format('GRANT EXECUTE ON FUNCTION pg_catalog.set_config(text, text, boolean) TO %I', current_user);
EXCEPTION WHEN insufficient_privilege THEN
    RAISE NOTICE 'could not revoke set_config from PUBLIC: %', SQLERRM;
END
$$;
