
-- Create a table for translated video metadata
CREATE TABLE public.translated_video_metadata (
  id TEXT NOT NULL PRIMARY KEY,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
  updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
  file_path TEXT NOT NULL,
  public_url TEXT NOT NULL,
  original_language TEXT,
  target_language TEXT,
  translated_text TEXT,
  duration INTEGER
);

-- Create a storage bucket for translated videos
INSERT INTO storage.buckets (id, name, public)
VALUES ('translated-videos', 'translated-videos', true);

-- Create storage policies for the translated-videos bucket
CREATE POLICY "Anyone can view translated videos" ON storage.objects
  FOR SELECT USING (bucket_id = 'translated-videos');

CREATE POLICY "Anyone can upload translated videos" ON storage.objects
  FOR INSERT WITH CHECK (bucket_id = 'translated-videos');

CREATE POLICY "Anyone can update translated videos" ON storage.objects
  FOR UPDATE USING (bucket_id = 'translated-videos');

CREATE POLICY "Anyone can delete translated videos" ON storage.objects
  FOR DELETE USING (bucket_id = 'translated-videos');
