import { Router, Response } from 'express';
import multer from 'multer';
import path from 'path';
import fs from 'fs';
import OpenAI from 'openai';
import { requireAuth, AuthenticatedRequest } from '../auth';

const router = Router();

const uploadsDir = path.resolve(process.env.UPLOADS_DIR || './uploads');
const upload = multer({ dest: uploadsDir });

let openai: OpenAI | null = null;
if (process.env.OPENAI_API_KEY) {
  openai = new OpenAI({ apiKey: process.env.OPENAI_API_KEY });
}

// Patterns indicating empty/hallucinated transcription
const HALLUCINATION_PATTERNS = [
  /thanks? for watching/i,
  /please (like|subscribe)/i,
  /\[.*\]/,
  /dziękuję za (oglądanie|uwagę)/i,
  /napisy? (wykonał|zrobiła?)/i,
  /subskrybuj/i,
  /www\./i,
];

function isHallucinationOrEmpty(text: string | null | undefined): boolean {
  if (!text || text.trim().length < 10) return true;
  return HALLUCINATION_PATTERNS.some((p) => p.test(text));
}

// POST /api/functions/transcribe-audio
router.post('/transcribe-audio', requireAuth, upload.single('file'), async (req: AuthenticatedRequest, res: Response): Promise<void> => {
  if (!req.file) {
    res.status(400).json({ message: 'No audio file uploaded' });
    return;
  }

  const tempPath = req.file.path;

  try {
    if (!openai) {
      res.status(503).json({ message: 'OpenAI API key not configured' });
      return;
    }

    const transcription = await openai.audio.transcriptions.create({
      file: fs.createReadStream(tempPath) as any,
      model: 'whisper-1',
      language: 'pl',
      response_format: 'text',
    });

    const text = typeof transcription === 'string' ? transcription : (transcription as any).text;
    const isEmptyOrHallucination = isHallucinationOrEmpty(text);

    res.json({
      transcription: isEmptyOrHallucination ? null : text,
      isEmptyOrHallucination,
    });
  } catch (err: any) {
    console.error('Transcription error:', err.message);
    res.status(500).json({ message: 'Transcription failed', error: err.message });
  } finally {
    fs.unlink(tempPath, () => {});
  }
});

export default router;
