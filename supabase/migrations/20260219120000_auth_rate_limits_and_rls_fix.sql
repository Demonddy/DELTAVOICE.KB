-- API usage tracking for authenticated rate limits (edge functions use service role)
CREATE TABLE IF NOT EXISTS public.api_usage (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
  feature TEXT NOT NULL,
  window_start TIMESTAMPTZ NOT NULL,
  request_count INTEGER NOT NULL DEFAULT 1,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (user_id, feature, window_start)
);

CREATE INDEX IF NOT EXISTS idx_api_usage_user_feature
  ON public.api_usage (user_id, feature, window_start DESC);

ALTER TABLE public.api_usage ENABLE ROW LEVEL SECURITY;

-- Users can read their own usage; writes only via service role (edge functions)
CREATE POLICY "users_read_own_api_usage" ON public.api_usage
  FOR SELECT
  USING (auth.uid() = user_id);

-- Tighten subscribers table: remove world-writable policies
DROP POLICY IF EXISTS "update_own_subscription" ON public.subscribers;
DROP POLICY IF EXISTS "insert_subscription" ON public.subscribers;

-- Users may only read their own subscription row
DROP POLICY IF EXISTS "select_own_subscription" ON public.subscribers;
CREATE POLICY "select_own_subscription" ON public.subscribers
  FOR SELECT
  USING (auth.uid() = user_id);

-- Storage: revoke public write/delete on translated-videos bucket
DROP POLICY IF EXISTS "Anyone can upload translated videos" ON storage.objects;
DROP POLICY IF EXISTS "Anyone can update translated videos" ON storage.objects;
DROP POLICY IF EXISTS "Anyone can delete translated videos" ON storage.objects;

-- Authenticated users may upload into their own folder only
CREATE POLICY "authenticated_upload_own_videos" ON storage.objects
  FOR INSERT
  TO authenticated
  WITH CHECK (
    bucket_id = 'translated-videos'
    AND (storage.foldername(name))[1] = auth.uid()::text
  );

CREATE POLICY "authenticated_update_own_videos" ON storage.objects
  FOR UPDATE
  TO authenticated
  USING (
    bucket_id = 'translated-videos'
    AND (storage.foldername(name))[1] = auth.uid()::text
  );

CREATE POLICY "authenticated_delete_own_videos" ON storage.objects
  FOR DELETE
  TO authenticated
  USING (
    bucket_id = 'translated-videos'
    AND (storage.foldername(name))[1] = auth.uid()::text
  );

-- Authenticated users can read their own files; keep public read for legacy URLs if needed
DROP POLICY IF EXISTS "Anyone can view translated videos" ON storage.objects;
CREATE POLICY "authenticated_read_own_videos" ON storage.objects
  FOR SELECT
  TO authenticated
  USING (
    bucket_id = 'translated-videos'
    AND (storage.foldername(name))[1] = auth.uid()::text
  );
