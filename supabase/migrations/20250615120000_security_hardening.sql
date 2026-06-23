-- Security hardening: admin roles, password reset rate limits, storage privacy, RLS verification

-- 1. Admin role on profiles (default 'user'; set to 'admin' manually for admins)
ALTER TABLE public.profiles
  ADD COLUMN IF NOT EXISTS role TEXT NOT NULL DEFAULT 'user';

ALTER TABLE public.profiles
  DROP CONSTRAINT IF EXISTS profiles_role_check;

ALTER TABLE public.profiles
  ADD CONSTRAINT profiles_role_check CHECK (role IN ('user', 'admin'));

-- Users read own profile; admins read all (via SECURITY DEFINER to avoid RLS recursion)
CREATE OR REPLACE FUNCTION public.is_admin()
RETURNS boolean
LANGUAGE sql
SECURITY DEFINER
STABLE
SET search_path = public
AS $$
  SELECT EXISTS (
    SELECT 1 FROM public.profiles
    WHERE id = auth.uid() AND role = 'admin'
  );
$$;

DROP POLICY IF EXISTS "Admins can view all profiles" ON public.profiles;
CREATE POLICY "Admins can view all profiles"
  ON public.profiles
  FOR SELECT
  USING (auth.uid() = id OR public.is_admin());

-- 2. Password reset rate limiting (max 3 per email per hour via edge function)
CREATE TABLE IF NOT EXISTS public.password_reset_rate_limits (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  email TEXT NOT NULL,
  window_start TIMESTAMPTZ NOT NULL,
  request_count INTEGER NOT NULL DEFAULT 1,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (email, window_start)
);

CREATE INDEX IF NOT EXISTS idx_password_reset_rate_limits_email
  ON public.password_reset_rate_limits (email, window_start DESC);

ALTER TABLE public.password_reset_rate_limits ENABLE ROW LEVEL SECURITY;
-- No client policies: only service role (edge functions) may read/write

-- 3. Make translated-videos bucket private (users access via RLS policies only)
UPDATE storage.buckets
SET public = false
WHERE id = 'translated-videos';

-- 4. Ensure voice_clones RLS is enabled (idempotent)
ALTER TABLE IF EXISTS public.voice_clones ENABLE ROW LEVEL SECURITY;

-- 5. Ensure translated_video_metadata RLS is enabled (idempotent)
ALTER TABLE IF EXISTS public.translated_video_metadata ENABLE ROW LEVEL SECURITY;

-- 6. Ensure voice_message_metadata RLS is enabled if table exists
DO $$
BEGIN
  IF EXISTS (
    SELECT 1 FROM information_schema.tables
    WHERE table_schema = 'public' AND table_name = 'voice_message_metadata'
  ) THEN
    EXECUTE 'ALTER TABLE public.voice_message_metadata ENABLE ROW LEVEL SECURITY';
  END IF;
END $$;

-- 7. Revoke any legacy permissive storage policies on translated-videos
DROP POLICY IF EXISTS "Anyone can view translated videos" ON storage.objects;
DROP POLICY IF EXISTS "Anyone can upload translated videos" ON storage.objects;
DROP POLICY IF EXISTS "Anyone can update translated videos" ON storage.objects;
DROP POLICY IF EXISTS "Anyone can delete translated videos" ON storage.objects;

-- Re-assert user-scoped storage policies (users only access files in their own folder)
DROP POLICY IF EXISTS "authenticated_upload_own_videos" ON storage.objects;
CREATE POLICY "authenticated_upload_own_videos" ON storage.objects
  FOR INSERT
  TO authenticated
  WITH CHECK (
    bucket_id = 'translated-videos'
    AND (storage.foldername(name))[1] = auth.uid()::text
  );

DROP POLICY IF EXISTS "authenticated_update_own_videos" ON storage.objects;
CREATE POLICY "authenticated_update_own_videos" ON storage.objects
  FOR UPDATE
  TO authenticated
  USING (
    bucket_id = 'translated-videos'
    AND (storage.foldername(name))[1] = auth.uid()::text
  );

DROP POLICY IF EXISTS "authenticated_delete_own_videos" ON storage.objects;
CREATE POLICY "authenticated_delete_own_videos" ON storage.objects
  FOR DELETE
  TO authenticated
  USING (
    bucket_id = 'translated-videos'
    AND (storage.foldername(name))[1] = auth.uid()::text
  );

DROP POLICY IF EXISTS "authenticated_read_own_videos" ON storage.objects;
CREATE POLICY "authenticated_read_own_videos" ON storage.objects
  FOR SELECT
  TO authenticated
  USING (
    bucket_id = 'translated-videos'
    AND (storage.foldername(name))[1] = auth.uid()::text
  );
