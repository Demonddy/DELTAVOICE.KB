-- Create table to store user voice clones
CREATE TABLE public.voice_clones (
  id UUID NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
  user_id UUID NOT NULL,
  name TEXT NOT NULL,
  elevenlabs_voice_id TEXT NOT NULL,
  is_default BOOLEAN DEFAULT false,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
  updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

-- Enable Row Level Security
ALTER TABLE public.voice_clones ENABLE ROW LEVEL SECURITY;

-- Create policies for user access
CREATE POLICY "Users can view their own voice clones" 
ON public.voice_clones 
FOR SELECT 
USING (auth.uid() = user_id);

CREATE POLICY "Users can create their own voice clones" 
ON public.voice_clones 
FOR INSERT 
WITH CHECK (auth.uid() = user_id);

CREATE POLICY "Users can update their own voice clones" 
ON public.voice_clones 
FOR UPDATE 
USING (auth.uid() = user_id);

CREATE POLICY "Users can delete their own voice clones" 
ON public.voice_clones 
FOR DELETE 
USING (auth.uid() = user_id);

-- Create trigger for automatic timestamp updates
CREATE TRIGGER update_voice_clones_updated_at
BEFORE UPDATE ON public.voice_clones
FOR EACH ROW
EXECUTE FUNCTION public.update_updated_at_column();