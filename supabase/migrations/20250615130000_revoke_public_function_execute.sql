-- Restrict SECURITY DEFINER helpers to service role only (not callable via PostgREST)
REVOKE ALL ON FUNCTION public.is_admin() FROM PUBLIC;
REVOKE ALL ON FUNCTION public.is_admin() FROM anon, authenticated;
REVOKE ALL ON FUNCTION public.get_current_user_id() FROM PUBLIC;
REVOKE ALL ON FUNCTION public.get_current_user_id() FROM anon, authenticated;
REVOKE ALL ON FUNCTION public.handle_new_user() FROM PUBLIC;
REVOKE ALL ON FUNCTION public.handle_new_user() FROM anon, authenticated;
