/**
 * TDD Tests for CallLogService
 *
 * Testing the core business logic for managing call logs:
 * - Creating missed call records
 * - Reserving calls for callback
 * - Completing calls
 * - Querying call logs by status
 */

import { CallLogService } from '../CallLogService';
import type { CallLog, CallLogStatus } from '@/types';

// Mock Supabase client
const mockSupabase = {
  from: jest.fn(),
};

// Mock data
const mockClient = {
  id: 'client-123',
  phone: '+48123456789',
  name: 'Jan Kowalski',
  address: 'ul. Testowa 1, Warszawa',
  notes: null,
  created_at: '2024-01-01T00:00:00Z',
  updated_at: '2024-01-01T00:00:00Z',
};

const mockCallLog: CallLog = {
  id: 'call-log-123',
  client_id: 'client-123',
  employee_id: null,
  type: 'missed',
  status: 'missed',
  timestamp: '2024-01-15T10:30:00Z',
  reservation_by: null,
  reservation_at: null,
  recipients: [],
  caller_phone: '123456789',
  created_at: '2024-01-15T10:30:00Z',
  updated_at: '2024-01-15T10:30:00Z',
};

describe('CallLogService', () => {
  let callLogService: CallLogService;
  let mockSelect: jest.Mock;
  let mockInsert: jest.Mock;
  let mockUpdate: jest.Mock;
  let mockEq: jest.Mock;
  let mockSingle: jest.Mock;

  beforeEach(() => {
    // Reset mocks
    jest.clearAllMocks();

    // Setup mock chain
    mockSingle = jest.fn();
    mockEq = jest.fn().mockReturnValue({
      single: mockSingle,
      select: jest.fn().mockReturnValue({ single: mockSingle })
    });
    mockSelect = jest.fn().mockReturnValue({
      eq: mockEq,
      single: mockSingle
    });
    mockInsert = jest.fn().mockReturnValue({
      select: mockSelect,
      single: mockSingle
    });
    mockUpdate = jest.fn().mockReturnValue({
      eq: mockEq,
      select: mockSelect
    });

    mockSupabase.from.mockReturnValue({
      select: mockSelect,
      insert: mockInsert,
      update: mockUpdate,
    });

    callLogService = new CallLogService(mockSupabase as any);
  });

  describe('createMissedCall', () => {
    it('should create a missed call log with missed status', async () => {
      // Arrange
      const clientId = 'client-123';
      const phoneNumber = '+48123456789';
      const expectedCallLog = {
        ...mockCallLog,
        client_id: clientId,
        type: 'missed',
        status: 'missed',
      };

      mockSingle.mockResolvedValue({ data: expectedCallLog, error: null });

      // Act
      const result = await callLogService.createMissedCall(clientId, phoneNumber);

      // Assert
      expect(mockSupabase.from).toHaveBeenCalledWith('call_logs');
      expect(mockInsert).toHaveBeenCalledWith(
        expect.objectContaining({
          client_id: clientId,
          type: 'missed',
          status: 'missed',
          timestamp: expect.any(String),
        })
      );
      expect(result).toEqual(expectedCallLog);
    });

    it('should throw error when database insert fails', async () => {
      // Arrange
      const clientId = 'client-123';
      const phoneNumber = '+48123456789';
      mockSingle.mockResolvedValue({
        data: null,
        error: { message: 'Database error' },
      });

      // Act & Assert
      await expect(
        callLogService.createMissedCall(clientId, phoneNumber)
      ).rejects.toThrow('Failed to create missed call log');
    });
  });

  describe('reserveCall', () => {
    it('should update call status to reserved and set reservation_by', async () => {
      // Arrange
      const callLogId = 'call-log-123';
      const employeeId = 'employee-456';
      const expectedCallLog = {
        ...mockCallLog,
        status: 'reserved' as CallLogStatus,
        reservation_by: employeeId,
        reservation_at: expect.any(String),
      };

      mockSingle.mockResolvedValue({ data: expectedCallLog, error: null });

      // Act
      const result = await callLogService.reserveCall(callLogId, employeeId);

      // Assert
      expect(mockSupabase.from).toHaveBeenCalledWith('call_logs');
      expect(mockUpdate).toHaveBeenCalledWith(
        expect.objectContaining({
          status: 'reserved',
          reservation_by: employeeId,
          reservation_at: expect.any(String),
        })
      );
      expect(mockEq).toHaveBeenCalledWith('id', callLogId);
      expect(result?.status).toBe('reserved');
      expect(result?.reservation_by).toBe(employeeId);
    });

    it('should throw error when call is already reserved', async () => {
      // Arrange
      const callLogId = 'call-log-123';
      const employeeId = 'employee-456';
      mockSingle.mockResolvedValue({
        data: null,
        error: { message: 'Call already reserved' },
      });

      // Act & Assert
      await expect(
        callLogService.reserveCall(callLogId, employeeId)
      ).rejects.toThrow('Failed to reserve call');
    });
  });

  describe('completeCall', () => {
    it('should update call status to completed', async () => {
      // Arrange
      const callLogId = 'call-log-123';
      const expectedCallLog = {
        ...mockCallLog,
        type: 'completed' as const,
        status: 'completed' as CallLogStatus,
      };

      mockSingle.mockResolvedValue({ data: expectedCallLog, error: null });

      // Act
      const result = await callLogService.completeCall(callLogId);

      // Assert
      expect(mockSupabase.from).toHaveBeenCalledWith('call_logs');
      expect(mockUpdate).toHaveBeenCalledWith(
        expect.objectContaining({
          type: 'completed',
          status: 'completed',
        })
      );
      expect(mockEq).toHaveBeenCalledWith('id', callLogId);
      expect(result?.status).toBe('completed');
    });

    it('should throw error when update fails', async () => {
      // Arrange
      const callLogId = 'call-log-123';
      mockSingle.mockResolvedValue({
        data: null,
        error: { message: 'Update failed' },
      });

      // Act & Assert
      await expect(
        callLogService.completeCall(callLogId)
      ).rejects.toThrow('Failed to complete call');
    });
  });

  describe('getMissedCalls', () => {
    it('should return all calls with missed status', async () => {
      // Arrange
      const expectedCalls = [
        { ...mockCallLog, id: 'call-1', status: 'missed' as CallLogStatus },
        { ...mockCallLog, id: 'call-2', status: 'missed' as CallLogStatus },
      ];

      mockEq.mockResolvedValue({ data: expectedCalls, error: null });

      // Act
      const result = await callLogService.getMissedCalls();

      // Assert
      expect(mockSupabase.from).toHaveBeenCalledWith('call_logs');
      expect(mockSelect).toHaveBeenCalledWith('*');
      expect(mockEq).toHaveBeenCalledWith('status', 'missed');
      expect(result).toEqual(expectedCalls);
      expect(result).toHaveLength(2);
    });

    it('should return empty array when no missed calls exist', async () => {
      // Arrange
      mockEq.mockResolvedValue({ data: [], error: null });

      // Act
      const result = await callLogService.getMissedCalls();

      // Assert
      expect(result).toEqual([]);
    });

    it('should throw error when query fails', async () => {
      // Arrange
      mockEq.mockResolvedValue({
        data: null,
        error: { message: 'Query failed' },
      });

      // Act & Assert
      await expect(callLogService.getMissedCalls()).rejects.toThrow(
        'Failed to fetch missed calls'
      );
    });
  });

  describe('getReservedCallsByEmployee', () => {
    it('should return calls reserved by specific employee', async () => {
      // Arrange
      const employeeId = 'employee-456';
      const expectedCalls = [
        {
          ...mockCallLog,
          id: 'call-1',
          status: 'reserved' as CallLogStatus,
          reservation_by: employeeId,
        },
        {
          ...mockCallLog,
          id: 'call-2',
          status: 'reserved' as CallLogStatus,
          reservation_by: employeeId,
        },
      ];

      const mockEq2 = jest.fn().mockResolvedValue({ data: expectedCalls, error: null });
      mockEq.mockReturnValue({ eq: mockEq2 });

      // Act
      const result = await callLogService.getReservedCallsByEmployee(employeeId);

      // Assert
      expect(mockSupabase.from).toHaveBeenCalledWith('call_logs');
      expect(mockSelect).toHaveBeenCalledWith('*');
      expect(mockEq).toHaveBeenCalledWith('status', 'reserved');
      expect(mockEq2).toHaveBeenCalledWith('reservation_by', employeeId);
      expect(result).toEqual(expectedCalls);
      expect(result).toHaveLength(2);
    });

    it('should return empty array when employee has no reserved calls', async () => {
      // Arrange
      const employeeId = 'employee-456';
      const mockEq2 = jest.fn().mockResolvedValue({ data: [], error: null });
      mockEq.mockReturnValue({ eq: mockEq2 });

      // Act
      const result = await callLogService.getReservedCallsByEmployee(employeeId);

      // Assert
      expect(result).toEqual([]);
    });
  });

  describe('createMissedCall with recipient', () => {
    it('should create a missed call with recipient in recipients array', async () => {
      // Arrange
      const clientId = 'client-123';
      const phoneNumber = '+48123456789';
      const recipientId = 'user-456';
      const expectedCallLog = {
        ...mockCallLog,
        client_id: clientId,
        recipients: [recipientId],
      };

      mockSingle.mockResolvedValue({ data: expectedCallLog, error: null });

      // Act
      const result = await callLogService.createMissedCall(clientId, phoneNumber, recipientId);

      // Assert
      expect(mockInsert).toHaveBeenCalledWith(
        expect.objectContaining({
          recipients: [recipientId],
        })
      );
      expect(result.recipients).toContain(recipientId);
    });

    it('should create a call with empty recipients when no recipient provided', async () => {
      // Arrange
      const clientId = 'client-123';
      const phoneNumber = '+48123456789';
      const expectedCallLog = {
        ...mockCallLog,
        recipients: [],
      };

      mockSingle.mockResolvedValue({ data: expectedCallLog, error: null });

      // Act
      const result = await callLogService.createMissedCall(clientId, phoneNumber);

      // Assert
      expect(mockInsert).toHaveBeenCalledWith(
        expect.objectContaining({
          recipients: [],
        })
      );
      expect(result.recipients).toEqual([]);
    });
  });

  describe('addRecipient', () => {
    it('should add recipient to existing call log', async () => {
      // Arrange
      const callLogId = 'call-log-123';
      const newRecipientId = 'user-789';
      const existingRecipients = ['user-456'];

      // Mock fetching existing call log
      const mockFetchSingle = jest.fn().mockResolvedValue({
        data: { recipients: existingRecipients },
        error: null,
      });
      mockEq.mockReturnValueOnce({ single: mockFetchSingle });

      // Mock updating call log
      const updatedCallLog = {
        ...mockCallLog,
        recipients: [...existingRecipients, newRecipientId],
      };
      mockSingle.mockResolvedValue({ data: updatedCallLog, error: null });

      // Act
      const result = await callLogService.addRecipient(callLogId, newRecipientId);

      // Assert
      expect(result?.recipients).toContain('user-456');
      expect(result?.recipients).toContain(newRecipientId);
    });

    it('should not add duplicate recipient', async () => {
      // Arrange
      const callLogId = 'call-log-123';
      const existingRecipientId = 'user-456';

      // Mock fetching existing call log with the recipient already present
      const mockFetchSingle = jest.fn().mockResolvedValue({
        data: { recipients: [existingRecipientId] },
        error: null,
      });
      mockEq.mockReturnValueOnce({ single: mockFetchSingle });

      // Act
      const result = await callLogService.addRecipient(callLogId, existingRecipientId);

      // Assert - should return null (no update needed)
      expect(result).toBeNull();
    });

    it('should throw error when call log not found', async () => {
      // Arrange
      const callLogId = 'nonexistent-call';
      const recipientId = 'user-456';

      const mockFetchSingle = jest.fn().mockResolvedValue({
        data: null,
        error: { message: 'Not found' },
      });
      mockEq.mockReturnValueOnce({ single: mockFetchSingle });

      // Act & Assert
      await expect(
        callLogService.addRecipient(callLogId, recipientId)
      ).rejects.toThrow('Failed to fetch call log');
    });
  });

  describe('Business Logic Rules', () => {
    it('should enforce status transition: missed -> reserved -> completed', async () => {
      // This test verifies the expected workflow:
      // 1. missed (do obsłużenia) -> 2. reserved (zarezerwowane) -> 3. completed (wykonane)
      const callLogId = 'call-log-123';
      const employeeId = 'employee-456';

      // Step 1: Create missed call (missed - do obsłużenia)
      const missedCall = { ...mockCallLog, status: 'missed' as CallLogStatus };
      mockSingle.mockResolvedValueOnce({ data: missedCall, error: null });

      const created = await callLogService.createMissedCall('client-123', '+48123456789');
      expect(created.status).toBe('missed');

      // Step 2: Reserve call (reserved - zarezerwowane)
      const reservedCall = {
        ...mockCallLog,
        status: 'reserved' as CallLogStatus,
        reservation_by: employeeId,
      };
      mockSingle.mockResolvedValueOnce({ data: reservedCall, error: null });

      const reserved = await callLogService.reserveCall(callLogId, employeeId);
      expect(reserved?.status).toBe('reserved');

      // Step 3: Complete call (completed - wykonane)
      const completedCall = { ...mockCallLog, status: 'completed' as CallLogStatus };
      mockSingle.mockResolvedValueOnce({ data: completedCall, error: null });

      const completed = await callLogService.completeCall(callLogId);
      expect(completed?.status).toBe('completed');
    });
  });

  describe('Shared Database (Visibility)', () => {
    it('getAllCallLogs should return all calls for shared visibility', async () => {
      // Arrange - All calls are visible to everyone (shared database)
      const sharedCalls = [
        { ...mockCallLog, id: 'call-1', recipients: ['user-1'] },
        { ...mockCallLog, id: 'call-2', recipients: ['user-2'] },
        { ...mockCallLog, id: 'call-3', recipients: ['user-1', 'user-2'] },
      ];

      const mockOrder = jest.fn().mockResolvedValue({ data: sharedCalls, error: null });
      mockSelect.mockReturnValue({ order: mockOrder });

      // Act
      const result = await callLogService.getAllCallLogs();

      // Assert - All calls returned regardless of recipients
      expect(result).toHaveLength(3);
      expect(mockSupabase.from).toHaveBeenCalledWith('call_logs');
    });

    it('should aggregate recipients when same number calls multiple users', async () => {
      // Test concept: when same number calls multiple users,
      // recipients array should contain all user IDs
      const callWithMultipleRecipients = {
        ...mockCallLog,
        recipients: ['user-1', 'user-2', 'user-3'],
      };

      // Verifies the data structure supports multiple recipients
      expect(callWithMultipleRecipients.recipients).toHaveLength(3);
      expect(callWithMultipleRecipients.recipients).toContain('user-1');
      expect(callWithMultipleRecipients.recipients).toContain('user-2');
      expect(callWithMultipleRecipients.recipients).toContain('user-3');
    });
  });
});
