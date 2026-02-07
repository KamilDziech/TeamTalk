/**
 * cleanup-audio Edge Function
 *
 * Deletes audio files from Storage bucket 'voice-reports' for records older than 30 days.
 * Sets audio_url = NULL after successful deletion.
 *
 * Deploy: npx supabase functions deploy cleanup-audio
 * Test: npx supabase functions invoke cleanup-audio
 *
 * Configure cron job in Supabase Dashboard:
 * - Schedule: 0 3 * * * (daily at 3:00 AM)
 * - Function: cleanup-audio
 */

import { createClient } from 'https://esm.sh/@supabase/supabase-js@2';

// Configuration
const DAYS_OLD = 30;
const BUCKET_NAME = 'voice-reports';

// Extract storage path from public URL
function extractStoragePath(publicUrl: string): string | null {
  // URL format: https://{project}.supabase.co/storage/v1/object/public/voice-reports/voice_reports/{filename}
  const match = publicUrl.match(/\/storage\/v1\/object\/public\/voice-reports\/(.+)$/);
  return match ? match[1] : null;
}

Deno.serve(async (req) => {
  try {
    // Get Supabase client with service role key (for admin operations)
    const supabaseUrl = Deno.env.get('SUPABASE_URL');
    const supabaseServiceKey = Deno.env.get('SUPABASE_SERVICE_ROLE_KEY');

    if (!supabaseUrl || !supabaseServiceKey) {
      throw new Error('Missing Supabase environment variables');
    }

    const supabase = createClient(supabaseUrl, supabaseServiceKey);

    console.log(`🧹 Starting audio cleanup for records older than ${DAYS_OLD} days...`);

    // Get old voice reports that need cleanup
    const { data: oldReports, error: fetchError } = await supabase.rpc(
      'get_old_voice_reports_for_cleanup',
      { days_old: DAYS_OLD }
    );

    if (fetchError) {
      console.error('Error fetching old reports:', fetchError);
      throw fetchError;
    }

    if (!oldReports || oldReports.length === 0) {
      console.log('✅ No audio files to clean up');
      return new Response(
        JSON.stringify({ message: 'No audio files to clean up', count: 0 }),
        { headers: { 'Content-Type': 'application/json' } }
      );
    }

    console.log(`📁 Found ${oldReports.length} audio files to clean up`);

    let successCount = 0;
    let errorCount = 0;

    // Process each old report
    for (const report of oldReports) {
      try {
        const storagePath = extractStoragePath(report.audio_url);

        if (!storagePath) {
          console.warn(`⚠️ Could not extract storage path from: ${report.audio_url}`);
          errorCount++;
          continue;
        }

        // Delete file from Storage
        const { error: deleteError } = await supabase.storage
          .from(BUCKET_NAME)
          .remove([storagePath]);

        if (deleteError) {
          console.error(`❌ Error deleting file ${storagePath}:`, deleteError);
          errorCount++;
          continue;
        }

        // Mark as cleaned in database
        const { error: updateError } = await supabase.rpc('mark_audio_cleaned', {
          report_id: report.id,
        });

        if (updateError) {
          console.error(`❌ Error updating report ${report.id}:`, updateError);
          errorCount++;
          continue;
        }

        console.log(`🗑️ Cleaned: ${storagePath}`);
        successCount++;
      } catch (err) {
        console.error(`❌ Error processing report ${report.id}:`, err);
        errorCount++;
      }
    }

    const summary = {
      message: 'Audio cleanup completed',
      total: oldReports.length,
      success: successCount,
      errors: errorCount,
    };

    console.log(`✅ Cleanup complete: ${successCount} deleted, ${errorCount} errors`);

    return new Response(JSON.stringify(summary), {
      headers: { 'Content-Type': 'application/json' },
    });
  } catch (error) {
    console.error('❌ Cleanup function error:', error);
    return new Response(
      JSON.stringify({ error: error.message }),
      { status: 500, headers: { 'Content-Type': 'application/json' } }
    );
  }
});
