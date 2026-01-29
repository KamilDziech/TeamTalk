/**
 * Call Monitoring Module
 *
 * Native module for detecting phone calls and monitoring call state
 * Android only - uses TelephonyManager and PhoneStateListener
 */

import { NativeModulesProxy, EventEmitter, Subscription } from 'expo-modules-core';

// Import the native module
const CallMonitoringModule = NativeModulesProxy.CallMonitoring;

export enum CallState {
  IDLE = 'IDLE',
  RINGING = 'RINGING',
  OFFHOOK = 'OFFHOOK',
}

export interface CallEvent {
  state: CallState;
  phoneNumber: string | null;
  timestamp: number;
}

export interface MissedCallEvent {
  phoneNumber: string;
  timestamp: number;
}

/**
 * Start monitoring phone calls
 * Requires READ_PHONE_STATE and READ_CALL_LOG permissions
 */
export async function startCallMonitoring(): Promise<void> {
  return await CallMonitoringModule.startMonitoring();
}

/**
 * Stop monitoring phone calls
 */
export async function stopCallMonitoring(): Promise<void> {
  return await CallMonitoringModule.stopMonitoring();
}

/**
 * Check if monitoring is currently active
 */
export async function isMonitoring(): Promise<boolean> {
  return await CallMonitoringModule.isMonitoring();
}

/**
 * Request required permissions
 */
export async function requestPermissions(): Promise<boolean> {
  return await CallMonitoringModule.requestPermissions();
}

/**
 * Check if required permissions are granted
 */
export async function hasPermissions(): Promise<boolean> {
  return await CallMonitoringModule.hasPermissions();
}

const emitter = new EventEmitter(CallMonitoringModule);

/**
 * Listen for call state changes
 */
export function addCallStateListener(
  listener: (event: CallEvent) => void
): Subscription {
  return emitter.addListener('onCallStateChanged', listener);
}

/**
 * Listen for missed calls
 */
export function addMissedCallListener(
  listener: (event: MissedCallEvent) => void
): Subscription {
  return emitter.addListener('onMissedCall', listener);
}

/**
 * Listen for call ended events
 */
export function addCallEndedListener(
  listener: (event: CallEvent) => void
): Subscription {
  return emitter.addListener('onCallEnded', listener);
}

export default {
  startCallMonitoring,
  stopCallMonitoring,
  isMonitoring,
  requestPermissions,
  hasPermissions,
  addCallStateListener,
  addMissedCallListener,
  addCallEndedListener,
  CallState,
};
