-- Phase 1: Critical RLS Policy Fixes

-- 1. Enable RLS on translated_video_metadata table and create user-specific policies
ALTER TABLE public.translated_video_metadata ENABLE ROW LEVEL SECURITY;

-- Add user_id column to translated_video_metadata for proper user isolation
ALTER TABLE public.translated_video_metadata ADD COLUMN user_id UUID REFERENCES auth.users(id) ON DELETE CASCADE;

-- Create RLS policies for translated_video_metadata
CREATE POLICY "Users can view their own translated videos" 
ON public.translated_video_metadata 
FOR SELECT 
USING (auth.uid() = user_id);

CREATE POLICY "Users can create their own translated videos" 
ON public.translated_video_metadata 
FOR INSERT 
WITH CHECK (auth.uid() = user_id);

CREATE POLICY "Users can update their own translated videos" 
ON public.translated_video_metadata 
FOR UPDATE 
USING (auth.uid() = user_id);

CREATE POLICY "Users can delete their own translated videos" 
ON public.translated_video_metadata 
FOR DELETE 
USING (auth.uid() = user_id);

-- 2. Replace overly permissive voice_message_metadata policy with user-restricted access
-- Add user_id column to voice_message_metadata
ALTER TABLE public.voice_message_metadata ADD COLUMN user_id UUID REFERENCES auth.users(id) ON DELETE CASCADE;

-- Drop the existing overly permissive policy
DROP POLICY IF EXISTS "Allow public access to voice message metadata" ON public.voice_message_metadata;

-- Create proper user-specific policies for voice_message_metadata
CREATE POLICY "Users can view their own voice messages" 
ON public.voice_message_metadata 
FOR SELECT 
USING (auth.uid() = user_id);

CREATE POLICY "Users can create their own voice messages" 
ON public.voice_message_metadata 
FOR INSERT 
WITH CHECK (auth.uid() = user_id);

CREATE POLICY "Users can update their own voice messages" 
ON public.voice_message_metadata 
FOR UPDATE 
USING (auth.uid() = user_id);

CREATE POLICY "Users can delete their own voice messages" 
ON public.voice_message_metadata 
FOR DELETE 
USING (auth.uid() = user_id);

-- Phase 2: Database Security Hardening

-- 3. Fix database function search_path vulnerabilities
CREATE OR REPLACE FUNCTION public.handle_new_user()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $function$
BEGIN
  INSERT INTO public.profiles (id, email, full_name)
  VALUES (NEW.id, NEW.email, NEW.raw_user_meta_data->>'full_name');
  RETURN NEW;
END;
$function$;

CREATE OR REPLACE FUNCTION public.update_updated_at_column()
RETURNS trigger
LANGUAGE plpgsql
SET search_path = public
AS $function$
BEGIN
  NEW.updated_at = now();
  RETURN NEW;
END;
$function$;

-- 4. Create security definer function for getting user roles (if roles are implemented later)
CREATE OR REPLACE FUNCTION public.get_current_user_id()
RETURNS UUID
LANGUAGE SQL
SECURITY DEFINER
STABLE
SET search_path = public
AS $$
  SELECT auth.uid();
$$;

-- 5. Add trigger for updated_at on new tables
CREATE TRIGGER update_translated_video_metadata_updated_at
BEFORE UPDATE ON public.translated_video_metadata
FOR EACH ROW
EXECUTE FUNCTION public.update_updated_at_column();

CREATE TRIGGER update_voice_message_metadata_updated_at
BEFORE UPDATE ON public.voice_message_metadata
FOR EACH ROW
EXECUTE FUNCTION public.update_updated_at_column();