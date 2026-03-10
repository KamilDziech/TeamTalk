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
import SimCardsManager from 'react-native-sim-cards-manager';
import AsyncStorage from '@react-native-async-storage/async-storage';
import { Platform, Alert } from 'react-native';
import { supabase } from '@/api/supabaseClient';

const BUSINESS_SIM_KEY = 'business_sim_id';
const DETECTED_SIMS_KEY = 'detected_sim_ids';
const DUAL_SIM_ENABLED_KEY = 'dual_sim_enabled';

interface SimInfo {
    id: string;
    displayName: string; // Short display name for UI (carrier name or SIM X)
    carrierName?: string; // Carrier/operator name
    slotIndex?: number; // SIM slot (0 or 1)
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
    private dualSimEnabled: boolean = false;
    private initialized: boolean = false;

    /**
     * Initialize service - load saved data from AsyncStorage
     */
    async initialize(): Promise<void> {
        if (this.initialized) return;

        try {
            // Load dual SIM enabled state
            const savedDualSimEnabled = await AsyncStorage.getItem(DUAL_SIM_ENABLED_KEY);
            if (savedDualSimEnabled) {
                this.dualSimEnabled = savedDualSimEnabled === 'true';
                console.log(`📱 Dual SIM mode: ${this.dualSimEnabled ? 'enabled' : 'disabled'}`);
            }

            // Load business SIM selection
            const savedBusinessSim = await AsyncStorage.getItem(BUSINESS_SIM_KEY);
            if (savedBusinessSim) {
                this.businessSimId = savedBusinessSim;
                console.log(`📱 Loaded business SIM ID: ${savedBusinessSim}`);
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
     * Detect available SIMs using SimCardsManager
     * Gets actual SIM card info from the device
     */
    async detectAvailableSims(): Promise<SimInfo[]> {
        if (Platform.OS !== 'android') {
            return [];
        }

        try {
            await this.initialize();

            // Use SimCardsManager to get actual SIM cards
            const simCards = await SimCardsManager.getSimCards({
                title: 'Dostęp do informacji o SIM',
                message: 'TeamTalk potrzebuje dostępu do informacji o kartach SIM aby filtrować połączenia.',
                buttonPositive: 'Zezwól',
                buttonNegative: 'Odmów',
            });

            console.log(`📱 SIM Cards found: ${simCards.length}`, JSON.stringify(simCards));

            if (simCards.length > 0) {
                // Map SIM cards to our SimInfo format
                // Use subscriptionId as the unique identifier (matches call log phoneAccountId)
                this.detectedSims = simCards.map((sim: any, index: number) => ({
                    id: sim.subscriptionId?.toString() || sim.simSlotIndex?.toString() || `sim_${index}`,
                    displayName: sim.carrierName || sim.displayName || `SIM ${index + 1}`,
                    carrierName: sim.carrierName,
                    slotIndex: sim.simSlotIndex,
                }));

                // Persist detected SIMs
                await AsyncStorage.setItem(DETECTED_SIMS_KEY, JSON.stringify(this.detectedSims));

                console.log(`📱 Detected ${this.detectedSims.length} SIM(s):`,
                    this.detectedSims.map(s => `${s.displayName} (${s.id})`).join(', '));
            }

            // Also check call logs for phoneAccountId mapping
            await this.detectSimIdsFromCallLog();

            return this.detectedSims;
        } catch (error) {
            console.error('Error detecting SIMs:', error);
            return this.detectedSims;
        }
    }

    /**
     * Detect SIM IDs from call log entries (for phoneAccountId mapping)
     * Returns array of unique phoneAccountIds found in call history
     */
    private async detectSimIdsFromCallLog(): Promise<string[]> {
        try {
            const callLogs: CallLogEntryWithSim[] = await CallLogs.load(100);
            const phoneAccountIds: string[] = [];

            if (callLogs.length > 0) {
                // Collect unique phoneAccountIds
                const seen = new Set<string>();
                for (const call of callLogs) {
                    if (call.phoneAccountId && !seen.has(call.phoneAccountId)) {
                        seen.add(call.phoneAccountId);
                        phoneAccountIds.push(call.phoneAccountId);
                    }
                }

                console.log(`📱 Found ${phoneAccountIds.length} unique phoneAccountIds in call log:`, phoneAccountIds);
            }

            return phoneAccountIds;
        } catch (error) {
            console.error('Error detecting SIM IDs from call log:', error);
            return [];
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
     * Check if multiple SIMs are enabled (manual setting or auto-detected)
     */
    async isMultipleSims(): Promise<boolean> {
        await this.initialize();

        // Check manual dual SIM setting first
        if (this.dualSimEnabled) {
            return true;
        }

        // Fallback to auto-detection
        if (this.detectedSims.length === 0) {
            await this.detectAvailableSims();
        }

        return this.detectedSims.length > 1;
    }

    /**
     * Check if manual dual SIM mode is enabled
     */
    async isDualSimEnabled(): Promise<boolean> {
        await this.initialize();
        return this.dualSimEnabled;
    }

    /**
     * Enable or disable manual dual SIM mode
     */
    async setDualSimEnabled(enabled: boolean): Promise<void> {
        this.dualSimEnabled = enabled;
        await AsyncStorage.setItem(DUAL_SIM_ENABLED_KEY, enabled ? 'true' : 'false');
        console.log(`📱 Dual SIM mode ${enabled ? 'enabled' : 'disabled'}`);

        // If enabling, detect real phoneAccountIds from call log
        if (enabled) {
            const phoneAccountIds = await this.detectSimIdsFromCallLog();

            if (phoneAccountIds.length > 0) {
                // Use real phoneAccountIds with friendly names
                this.detectedSims = phoneAccountIds.map((id, index) => ({
                    id: id,
                    displayName: `SIM ${index + 1}`,
                    carrierName: undefined,
                    slotIndex: index,
                }));
                console.log(`📱 Created ${this.detectedSims.length} SIM entries from call log phoneAccountIds`);
            } else {
                // Fallback to dummy entries if no call history
                this.detectedSims = [
                    { id: 'sim_1', displayName: 'SIM 1' },
                    { id: 'sim_2', displayName: 'SIM 2' },
                ];
                console.log('📱 No phoneAccountIds found in call log, using dummy SIM entries');
            }

            await AsyncStorage.setItem(DETECTED_SIMS_KEY, JSON.stringify(this.detectedSims));
        }
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
     * Refresh SIM detection from call log
     * Call this to update SIM entries with real phoneAccountIds
     */
    async refreshSimDetection(): Promise<SimInfo[]> {
        await this.initialize();

        const phoneAccountIds = await this.detectSimIdsFromCallLog();

        if (phoneAccountIds.length > 0) {
            this.detectedSims = phoneAccountIds.map((id, index) => ({
                id: id,
                displayName: `SIM ${index + 1}`,
                carrierName: undefined,
                slotIndex: index,
            }));
            await AsyncStorage.setItem(DETECTED_SIMS_KEY, JSON.stringify(this.detectedSims));
            console.log(`📱 Refreshed SIM detection: ${this.detectedSims.length} SIMs found`);
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
     * Set the business SIM ID and optionally clean up call logs from wrong SIM
     */
    async setBusinessSimId(simId: string): Promise<void> {
        const oldSimId = this.businessSimId;
        this.businessSimId = simId;
        await AsyncStorage.setItem(BUSINESS_SIM_KEY, simId);
        console.log(`📱 Set business SIM ID: ${this.shortenId(simId)}`);

        // Ask to clean up call_logs from wrong SIM (only if we're changing from one SIM to another)
        if (oldSimId && oldSimId !== simId) {
            await this.askAndCleanupWrongSimCallLogs(simId);
        }
    }

    /**
     * Ask user if they want to delete calls from wrong SIM, then clean up if confirmed
     */
    private async askAndCleanupWrongSimCallLogs(businessSimId: string): Promise<void> {
        try {
            // First check how many calls would be deleted
            const { data, error } = await supabase
                .from('call_logs')
                .select('id')
                .neq('phone_account_id', businessSimId)
                .not('phone_account_id', 'is', null)
                .eq('status', 'missed');

            if (error) {
                console.error('Error checking wrong SIM call logs:', error);
                return;
            }

            const count = data?.length || 0;
            if (count === 0) {
                console.log('📱 No calls from wrong SIM to delete');
                return;
            }

            // Ask user for confirmation
            return new Promise((resolve) => {
                Alert.alert(
                    'Usunąć połączenia?',
                    `W kolejce jest ${count} połączeń z poprzedniej karty SIM. Czy chcesz je usunąć?`,
                    [
                        {
                            text: 'Nie, zostaw',
                            style: 'cancel',
                            onPress: () => {
                                console.log('📱 User chose to keep calls from wrong SIM');
                                resolve();
                            },
                        },
                        {
                            text: 'Tak, usuń',
                            style: 'destructive',
                            onPress: async () => {
                                await this.cleanupWrongSimCallLogs(businessSimId);
                                resolve();
                            },
                        },
                    ]
                );
            });
        } catch (error) {
            console.error('Error in askAndCleanupWrongSimCallLogs:', error);
        }
    }

    /**
     * Delete call_logs that don't match the business SIM
     * Only deletes calls that are still in 'missed' status (not yet processed)
     */
    private async cleanupWrongSimCallLogs(businessSimId: string): Promise<void> {
        try {
            // Delete call_logs where:
            // - phone_account_id is NOT NULL (we know which SIM it came from)
            // - phone_account_id does NOT match the business SIM
            // - status is 'missed' (not yet reserved/completed)
            const { data, error } = await supabase
                .from('call_logs')
                .delete()
                .neq('phone_account_id', businessSimId)
                .not('phone_account_id', 'is', null)
                .eq('status', 'missed')
                .select('id');

            if (error) {
                console.error('Error cleaning up wrong SIM call logs:', error);
                return;
            }

            const deletedCount = data?.length || 0;
            if (deletedCount > 0) {
                console.log(`📱 Deleted ${deletedCount} call logs from wrong SIM`);
            }
        } catch (error) {
            console.error('Error in cleanupWrongSimCallLogs:', error);
        }
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
