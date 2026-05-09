import 'dotenv/config';
import express from 'express';
import cors from 'cors';
import path from 'path';
import { pool } from './db';
import authRoutes from './routes/auth';
import clientsRoutes from './routes/clients';
import callLogsRoutes from './routes/callLogs';
import voiceReportsRoutes from './routes/voiceReports';
import profilesRoutes from './routes/profiles';
import devicesRoutes from './routes/devices';
import storageRoutes from './routes/storage';
import functionsRoutes from './routes/functions';

const app = express();
const PORT = parseInt(process.env.PORT || '3000');

app.use(cors({
  origin: '*',
  methods: ['GET', 'POST', 'PUT', 'PATCH', 'DELETE', 'OPTIONS'],
  allowedHeaders: ['Content-Type', 'Authorization', 'X-Upsert', 'X-On-Conflict'],
}));

app.use(express.json({ limit: '10mb' }));
app.use(express.urlencoded({ extended: true }));

// Health check
app.get('/health', (_req, res) => {
  res.json({ status: 'ok', timestamp: new Date().toISOString() });
});

// API routes
app.use('/api/auth', authRoutes);
app.use('/api/clients', clientsRoutes);
app.use('/api/call-logs', callLogsRoutes);
app.use('/api/voice-reports', voiceReportsRoutes);
app.use('/api/profiles', profilesRoutes);
app.use('/api/devices', devicesRoutes);
app.use('/api/storage', storageRoutes);
app.use('/api/functions', functionsRoutes);

// Global error handler
app.use((err: any, _req: express.Request, res: express.Response, _next: express.NextFunction) => {
  console.error('Unhandled error:', err.message);
  res.status(500).json({ message: 'Internal server error' });
});

async function start() {
  try {
    await pool.query('SELECT 1');
    console.log('✅ PostgreSQL connected');

    app.listen(PORT, '0.0.0.0', () => {
      console.log(`🚀 TeamTalk API server running on port ${PORT}`);
    });
  } catch (err: any) {
    console.error('❌ Failed to connect to PostgreSQL:', err.message);
    process.exit(1);
  }
}

start();
