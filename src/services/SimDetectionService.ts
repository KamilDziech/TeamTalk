/**
 * SimDetectionService
 *
 * Manages Dual SIM detection and business SIM selection.
 * - Detects unique phone_account_id / subscription_id from call log
 * - Persists business SIM choice in AsyncStorage
 * - Provides filtering logic for CallLogScanner
 *
 * NOTE: SIM IDs are extracted from Android CallLog entries.
 * The format may vary between manufacturers.
 */

import CallLogs from 'react-native-call-log';
import AsyncStorage from '@react-native-async-storage/async-storage';
import { Platform } from 'react-native';

const BUSINESS_SIM_KEY = 'business_sim_id';
const DETECTED_SIMS_KEY = 'detected_sim_ids';

interface SimInfo {
    id: string;
    displayName: string; // Short display name for UI
}

interface CallLogEntryWithSim {
    phoneNumber: string;
    type: string;
    dateTime: string | number;
    duration: number;
    // SIM identification fields (may vary by Android version/manufacturer)
    phoneAccountId?: string;
    subscriptionId?: string | number;
    simId?: string | number;
}

class SimDetectionService {
    private detectedSims: SimInfo[] = [];
    private businessSimId: string | null = null;
    private initialized: boolean = false;

    /**
     * Initialize service - load saved data from AsyncStorage
     */
    async initialize(): Promise<void> {
        if (this.initialized) return;

        try {
            // Load business SIM selection
            const savedBusinessSim = await AsyncStorage.getItem(BUSINESS_SIM_KEY);
            if (savedBusinessSim) {
                this.businessSimId = savedBusinessSim;
                console.log(`📱 Loaded business SIM ID: ${this.shortenId(savedBusinessSim)}`);
            }

            // Load detected SIMs
            const savedSims = await AsyncStorage.getItem(DETECTED_SIMS_KEY);
            if (savedSims) {
                this.detectedSims = JSON.parse(savedSims);
                console.log(`📱 Loaded ${this.detectedSims.length} detected SIMs`);
            }
        } catch (error) {
            console.error('Error initializing SimDetectionService:', error);
        }

        this.initialized = true;
    }

    /**
     * Detect available SIMs from call log entries
     * Scans recent call logs and extracts unique SIM identifiers
     */
    async detectAvailableSims(): Promise<SimInfo[]> {
        if (Platform.OS !== 'android') {
            return [];
        }

        try {
            await this.initialize();

            // Scan last 100 calls to detect SIM IDs
            const callLogs: CallLogEntryWithSim[] = await CallLogs.load(100);

            const simIds = new Set<string>();

            for (const call of callLogs) {
                const simId = this.extractSimId(call);
                if (simId) {
                    simIds.add(simId);
                }
            }

            // Convert to SimInfo array
            this.detectedSims = Array.from(simIds).map((id, index) => ({
                id,
                displayName: `SIM ${index + 1}`,
            }));

            // Persist detected SIMs
            await AsyncStorage.setItem(DETECTED_SIMS_KEY, JSON.stringify(this.detectedSims));

            console.log(`📱 Detected ${this.detectedSims.length} SIM(s): ${this.detectedSims.map(s => this.shortenId(s.id)).join(', ')}`);

            return this.detectedSims;
        } catch (error) {
            console.error('Error detecting SIMs:', error);
            return this.detectedSims;
        }
    }

    /**
     * Extract SIM identifier from call log entry
     * Handles different field names across Android versions
     */
    private extractSimId(call: CallLogEntryWithSim): string | null {
        // Try different field names that may contain SIM ID
        if (call.phoneAccountId && typeof call.phoneAccountId === 'string') {
            return call.phoneAccountId;
        }
        if (call.subscriptionId !== undefined) {
            return String(call.subscriptionId);
        }
        if (call.simId !== undefined) {
            return String(call.simId);
        }
        return null;
    }

    /**
     * Check if multiple SIMs were detected
     */
    async isMultipleSims(): Promise<boolean> {
        await this.initialize();

        // If we haven't detected yet, do it now
        if (this.detectedSims.length === 0) {
            await this.detectAvailableSims();
        }

        return this.detectedSims.length > 1;
    }

    /**
     * Get list of detected SIMs
     */
    async getDetectedSims(): Promise<SimInfo[]> {
        await this.initialize();

        if (this.detectedSims.length === 0) {
            await this.detectAvailableSims();
        }

        return this.detectedSims;
    }

    /**
     * Get currently selected business SIM ID
     */
    async getBusinessSimId(): Promise<string | null> {
        await this.initialize();
        return this.businessSimId;
    }

    /**
     * Set the business SIM ID
     */
    async setBusinessSimId(simId: string): Promise<void> {
        this.businessSimId = simId;
        await AsyncStorage.setItem(BUSINESS_SIM_KEY, simId);
        console.log(`📱 Set business SIM ID: ${this.shortenId(simId)}`);
    }

    /**
     * Reset SIM selection (clear business SIM choice)
     */
    async resetSimSelection(): Promise<void> {
        this.businessSimId = null;
        await AsyncStorage.removeItem(BUSINESS_SIM_KEY);
        console.log('📱 Reset SIM selection');
    }

    /**
     * Check if a call should be filtered out (not business SIM)
     * Returns true if call should be IGNORED
     */
    async shouldFilterCall(call: CallLogEntryWithSim): Promise<boolean> {
        await this.initialize();

        // If no multiple SIMs detected, don't filter
        if (this.detectedSims.length <= 1) {
            return false;
        }

        // If no business SIM selected, don't filter (let user select first)
        if (!this.businessSimId) {
            return false;
        }

        const callSimId = this.extractSimId(call);

        // If we can't determine SIM, don't filter
        if (!callSimId) {
            return false;
        }

        // Filter if call is from non-business SIM
        return callSimId !== this.businessSimId;
    }

    /**
     * Check if SIM selection is needed (multiple SIMs but no selection)
     */
    async isSimSelectionNeeded(): Promise<boolean> {
        await this.initialize();

        if (this.detectedSims.length === 0) {
            await this.detectAvailableSims();
        }

        return this.detectedSims.length > 1 && !this.businessSimId;
    }

    /**
     * Shorten SIM ID for display (first 8 chars + ...)
     */
    shortenId(id: string): string {
        if (id.length <= 12) return id;
        return `${id.substring(0, 8)}...`;
    }
}

// Export singleton instance
export const simDetectionService = new SimDetectionService();
