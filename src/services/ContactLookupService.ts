/**
 * ContactLookupService
 *
 * Provides phone number to contact name lookup using device's native contacts.
 * Priority order:
 * 1. Device contacts (phone's address book)
 * 2. CRM clients (app's database) - as fallback
 */

import * as Contacts from 'expo-contacts';

// Cache to avoid repeated lookups
const contactCache = new Map<string, string | null>();
let contactsLoaded = false;
let contactsPermissionGranted = false;

/**
 * Normalize phone number for comparison
 * Removes spaces, dashes, parentheses and country codes
 */
const normalizePhoneNumber = (phone: string): string => {
    // Remove all non-digit characters
    let normalized = phone.replace(/\D/g, '');

    // Remove common country codes (48 for Poland, 1 for US, etc.)
    if (normalized.startsWith('48') && normalized.length > 9) {
        normalized = normalized.substring(2);
    } else if (normalized.startsWith('0048') && normalized.length > 11) {
        normalized = normalized.substring(4);
    }

    // Take last 9 digits for comparison (standard phone number length)
    if (normalized.length > 9) {
        normalized = normalized.slice(-9);
    }

    return normalized;
};

/**
 * Load all contacts from device into cache
 */
export const loadDeviceContacts = async (): Promise<boolean> => {
    try {
        console.log('📱 Requesting contacts permissions...');
        const { status } = await Contacts.requestPermissionsAsync();
        console.log(`📱 Contacts permission status: ${status}`);

        if (status !== 'granted') {
            console.log('📱 Contacts permission NOT granted - caller ID will not work');
            contactsPermissionGranted = false;
            return false;
        }

        contactsPermissionGranted = true;
        console.log('📱 Fetching contacts from device...');

        const { data } = await Contacts.getContactsAsync({
            fields: [
                Contacts.Fields.Name,
                Contacts.Fields.FirstName,
                Contacts.Fields.LastName,
                Contacts.Fields.PhoneNumbers,
            ],
        });

        console.log(`📱 Retrieved ${data.length} contacts from device`);

        // Build cache: normalized phone -> contact name
        contactCache.clear();

        for (const contact of data) {
            if (!contact.phoneNumbers || contact.phoneNumbers.length === 0) continue;

            const name = contact.name ||
                `${contact.firstName || ''} ${contact.lastName || ''}`.trim() ||
                null;

            if (!name) continue;

            for (const phoneEntry of contact.phoneNumbers) {
                if (phoneEntry.number) {
                    const normalizedPhone = normalizePhoneNumber(phoneEntry.number);
                    if (normalizedPhone && normalizedPhone.length >= 7) {
                        contactCache.set(normalizedPhone, name);
                    }
                }
            }
        }

        contactsLoaded = true;
        console.log(`📱 ✅ Loaded ${contactCache.size} phone numbers from ${data.length} contacts`);
        return true;
    } catch (error) {
        console.error('📱 ❌ Error loading device contacts:', error);
        return false;
    }
};

/**
 * Look up contact name by phone number
 * Returns null if not found in device contacts
 */
export const lookupContactName = (phone: string | null): string | null => {
    if (!phone || !contactsLoaded) return null;
    const normalizedPhone = normalizePhoneNumber(phone);
    return contactCache.get(normalizedPhone) || null;
};

/**
 * Check if contacts are loaded
 */
export const areContactsLoaded = (): boolean => {
    return contactsLoaded;
};

/**
 * Check if contacts permission is granted
 */
export const hasContactsPermission = (): boolean => {
    return contactsPermissionGranted;
};

/**
 * Force reload contacts from device
 */
export const reloadContacts = async (): Promise<boolean> => {
    contactsLoaded = false;
    contactCache.clear();
    return loadDeviceContacts();
};

export const contactLookupService = {
    loadDeviceContacts,
    lookupContactName,
    areContactsLoaded,
    hasContactsPermission,
    reloadContacts,
    normalizePhoneNumber,
};
