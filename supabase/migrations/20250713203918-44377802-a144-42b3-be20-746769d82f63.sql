-- Add converted_audio field to translated_video_metadata table
ALTER TABLE public.translated_video_metadata 
ADD COLUMN converted_audio TEXT;