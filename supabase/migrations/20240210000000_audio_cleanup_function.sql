-- Audio cleanup function for voice reports older than 30 days
-- This function identifies records that need audio cleanup

-- Function to get voice reports with audio files older than 30 days
CREATE OR REPLACE FUNCTION get_old_voice_reports_for_cleanup(days_old INTEGER DEFAULT 30)
RETURNS TABLE (
  id UUID,
  audio_url TEXT,
  created_at TIMESTAMPTZ
) AS $$
BEGIN
  RETURN QUERY
  SELECT
    vr.id,
    vr.audio_url,
    vr.created_at
  FROM voice_reports vr
  WHERE vr.audio_url IS NOT NULL
    AND vr.created_at < NOW() - (days_old || ' days')::INTERVAL;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- Function to mark audio as cleaned up (set audio_url to NULL)
CREATE OR REPLACE FUNCTION mark_audio_cleaned(report_id UUID)
RETURNS VOID AS $$
BEGIN
  UPDATE voice_reports
  SET audio_url = NULL,
      updated_at = NOW()
  WHERE id = report_id;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- Comment explaining the functions
COMMENT ON FUNCTION get_old_voice_reports_for_cleanup IS 'Returns voice reports with audio files older than specified days for cleanup';
COMMENT ON FUNCTION mark_audio_cleaned IS 'Marks a voice report as cleaned (removes audio_url reference)';
