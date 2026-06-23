-- Prevent profile role trigger helpers from being invoked via PostgREST RPC
REVOKE ALL ON FUNCTION public.enforce_profile_role_on_insert() FROM PUBLIC;
REVOKE ALL ON FUNCTION public.enforce_profile_role_on_insert() FROM anon, authenticated;
REVOKE ALL ON FUNCTION public.prevent_profile_role_escalation() FROM PUBLIC;
REVOKE ALL ON FUNCTION public.prevent_profile_role_escalation() FROM anon, authenticated;
