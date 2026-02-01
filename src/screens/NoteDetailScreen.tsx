/**
 * NoteDetailScreen
 *
 * Detail screen showing full note content for a completed call.
 * Clean, paper-like design focused on readability.
 */

import React, { useState, useEffect } from 'react';
import {
    View,
    Text,
    StyleSheet,
    ScrollView,
    TouchableOpacity,
    RefreshControl,
} from 'react-native';
import { useRoute, RouteProp } from '@react-navigation/native';
import { MaterialIcons } from '@expo/vector-icons';
import { voiceReportService } from '@/services/VoiceReportService';
import { contactLookupService } from '@/services/ContactLookupService';
import { useTheme } from '@/contexts/ThemeContext';
import { spacing, radius, typography } from '@/styles/theme';
import type { HistoryStackParamList } from '@/navigation/HistoryStackNavigator';

type NoteDetailRouteProp = RouteProp<HistoryStackParamList, 'NoteDetail'>;

/**
 * Format date in human-readable format
 */
const formatDate = (timestamp: string): string => {
    const date = new Date(timestamp);
    const now = new Date();
    const diffMs = now.getTime() - date.getTime();
    const diffMins = Math.floor(diffMs / (1000 * 60));
    const diffHours = Math.floor(diffMins / 60);
    const diffDays = Math.floor(diffHours / 24);

    if (diffMins < 60) {
        return `${diffMins} min temu`;
    } else if (diffHours < 24) {
        return `${diffHours}h temu`;
    } else if (diffDays === 1) {
        return `Wczoraj, ${date.toLocaleTimeString('pl-PL', { hour: '2-digit', minute: '2-digit' })}`;
    } else if (diffDays < 7) {
        return `${diffDays} dni temu`;
    } else {
        return date.toLocaleDateString('pl-PL', {
            day: '2-digit',
            month: '2-digit',
            year: 'numeric',
            hour: '2-digit',
            minute: '2-digit',
        });
    }
};

/**
 * Format full date
 */
const formatFullDate = (timestamp: string): string => {
    const date = new Date(timestamp);
    return date.toLocaleDateString('pl-PL', {
        weekday: 'long',
        day: 'numeric',
        month: 'long',
        year: 'numeric',
        hour: '2-digit',
        minute: '2-digit',
    });
};

export const NoteDetailScreen: React.FC = () => {
    const route = useRoute<NoteDetailRouteProp>();
    const { item } = route.params;
    const { colors } = useTheme();
    const styles = createStyles(colors);
    const [isPlaying, setIsPlaying] = useState(false);
    const [refreshing, setRefreshing] = useState(false);

    // Get display name from device contacts or CRM
    const phoneNumber = item.client?.phone || item.callLog.caller_phone || null;
    const deviceContactName = contactLookupService.lookupContactName(phoneNumber);
    const crmClientName = item.client?.name || null;
    const displayName = deviceContactName || crmClientName || phoneNumber || 'Nieznany numer';
    const displayPhone = (deviceContactName || crmClientName) ? phoneNumber : null;

    const handlePlayAudio = async () => {
        if (!item.voiceReport.audio_url) return;

        try {
            if (isPlaying) {
                await voiceReportService.stopPlayback();
                setIsPlaying(false);
            } else {
                setIsPlaying(true);
                await voiceReportService.playAudio(item.voiceReport.audio_url);
            }
        } catch (error) {
            console.error('Error playing audio:', error);
            setIsPlaying(false);
        }
    };

    const onRefresh = () => {
        setRefreshing(true);
        // In real app, would refetch data
        setTimeout(() => setRefreshing(false), 500);
    };

    // Parse markdown-like summary into readable format
    const renderNoteContent = (content: string | null) => {
        if (!content) return null;

        const lines = content.split('\n').filter((line) => line.trim());

        return (
            <View style={styles.noteContent}>
                {lines.map((line, index) => {
                    const isHeader = line.startsWith('**') || line.startsWith('#');
                    const isBullet = line.startsWith('-') || line.startsWith('•');
                    const isNumbered = /^\d+\./.test(line.trim());

                    let displayLine = line
                        .replace(/\*\*/g, '')
                        .replace(/^#+\s*/, '')
                        .replace(/^-\s*/, '')
                        .replace(/^•\s*/, '')
                        .trim();

                    if (isHeader) {
                        return (
                            <Text key={index} style={styles.noteHeader}>
                                {displayLine}
                            </Text>
                        );
                    }

                    if (isBullet || isNumbered) {
                        return (
                            <View key={index} style={styles.bulletRow}>
                                <Text style={styles.bullet}>•</Text>
                                <Text style={styles.bulletText}>{displayLine}</Text>
                            </View>
                        );
                    }

                    return (
                        <Text key={index} style={styles.noteParagraph}>
                            {displayLine}
                        </Text>
                    );
                })}
            </View>
        );
    };

    return (
        <ScrollView
            style={styles.container}
            contentContainerStyle={styles.content}
            refreshControl={
                <RefreshControl refreshing={refreshing} onRefresh={onRefresh} />
            }
        >
            {/* Header */}
            <View style={styles.header}>
                <Text style={styles.displayName}>{displayName}</Text>
                {displayPhone && (
                    <Text style={styles.phoneNumber}>{displayPhone}</Text>
                )}
                <Text style={styles.dateText}>{formatFullDate(item.callLog.timestamp)}</Text>
            </View>

            {/* Separator */}
            <View style={styles.separator} />

            {/* AI Summary Section */}
            {item.voiceReport.ai_summary && (
                <View style={styles.noteSection}>
                    <View style={styles.sectionHeader}>
                        <MaterialIcons name="auto-awesome" size={18} color={colors.primary} />
                        <Text style={styles.sectionTitle}>Streszczenie AI</Text>
                    </View>
                    <View style={styles.notePaper}>
                        {renderNoteContent(item.voiceReport.ai_summary)}
                    </View>
                </View>
            )}

            {/* Full Transcription Section */}
            {item.voiceReport.transcription && (
                <View style={styles.noteSection}>
                    <View style={styles.sectionHeader}>
                        <MaterialIcons name="description" size={18} color={colors.textSecondary} />
                        <Text style={styles.sectionTitle}>Pełna notatka</Text>
                    </View>
                    <View style={styles.notePaper}>
                        <Text style={styles.transcriptionText}>
                            {item.voiceReport.transcription}
                        </Text>
                    </View>
                </View>
            )}

            {/* Audio Player */}
            {item.voiceReport.audio_url && (
                <TouchableOpacity
                    style={[styles.audioButton, isPlaying && styles.audioButtonActive]}
                    onPress={handlePlayAudio}
                >
                    <MaterialIcons
                        name={isPlaying ? 'stop' : 'play-arrow'}
                        size={24}
                        color={isPlaying ? colors.textInverse : colors.primary}
                    />
                    <Text style={[styles.audioButtonText, isPlaying && styles.audioButtonTextActive]}>
                        {isPlaying ? 'Zatrzymaj' : 'Odtwórz nagranie'}
                    </Text>
                </TouchableOpacity>
            )}

            {/* Info Footer */}
            <View style={styles.footer}>
                <Text style={styles.footerText}>
                    Zakończono: {formatDate(item.callLog.timestamp)}
                </Text>
            </View>
        </ScrollView>
    );
};

// Dynamic styles generator
const createStyles = (colors: ReturnType<typeof import('@/contexts/ThemeContext').useTheme>['colors']) =>
    StyleSheet.create({
        container: {
            flex: 1,
            backgroundColor: colors.surface,
        },
        content: {
            padding: spacing.lg,
            paddingBottom: spacing.xxxl,
        },

        // Header
        header: {
            marginBottom: spacing.md,
        },
        displayName: {
            fontSize: 28,
            fontWeight: typography.bold,
            color: colors.textPrimary,
            letterSpacing: 0.5,
        },
        phoneNumber: {
            fontSize: typography.base,
            color: colors.textSecondary,
            marginTop: spacing.xs,
        },
        dateText: {
            fontSize: typography.sm,
            color: colors.textTertiary,
            marginTop: spacing.sm,
        },

        // Separator
        separator: {
            height: 1,
            backgroundColor: colors.borderLight,
            marginVertical: spacing.lg,
        },

        // Note Section
        noteSection: {
            marginBottom: spacing.lg,
        },
        sectionHeader: {
            flexDirection: 'row',
            alignItems: 'center',
            marginBottom: spacing.sm,
        },
        sectionTitle: {
            fontSize: typography.base,
            fontWeight: typography.semibold,
            color: colors.textSecondary,
            marginLeft: spacing.sm,
        },

        // Paper-like note area
        notePaper: {
            backgroundColor: colors.background,
            borderRadius: radius.lg,
            padding: spacing.lg,
            borderLeftWidth: 4,
            borderLeftColor: colors.primary,
        },
        noteContent: {},
        noteHeader: {
            fontSize: typography.base,
            fontWeight: typography.semibold,
            color: colors.textPrimary,
            marginBottom: spacing.sm,
            marginTop: spacing.sm,
        },
        noteParagraph: {
            fontSize: typography.base,
            color: colors.textPrimary,
            lineHeight: 24,
            marginBottom: spacing.sm,
        },
        bulletRow: {
            flexDirection: 'row',
            marginBottom: spacing.xs,
            paddingLeft: spacing.sm,
        },
        bullet: {
            fontSize: typography.base,
            color: colors.primary,
            marginRight: spacing.sm,
            lineHeight: 24,
        },
        bulletText: {
            flex: 1,
            fontSize: typography.base,
            color: colors.textPrimary,
            lineHeight: 24,
        },
        transcriptionText: {
            fontSize: typography.base,
            color: colors.textPrimary,
            lineHeight: 26,
        },

        // Audio Button
        audioButton: {
            flexDirection: 'row',
            alignItems: 'center',
            justifyContent: 'center',
            backgroundColor: 'transparent',
            borderWidth: 1,
            borderColor: colors.primary,
            borderRadius: radius.lg,
            paddingVertical: spacing.md,
            paddingHorizontal: spacing.lg,
            marginBottom: spacing.lg,
        },
        audioButtonActive: {
            backgroundColor: colors.error,
            borderColor: colors.error,
        },
        audioButtonText: {
            fontSize: typography.base,
            fontWeight: typography.semibold,
            color: colors.primary,
            marginLeft: spacing.sm,
        },
        audioButtonTextActive: {
            color: colors.textInverse,
        },

        // Footer
        footer: {
            alignItems: 'center',
            paddingTop: spacing.lg,
        },
        footerText: {
            fontSize: typography.sm,
            color: colors.textTertiary,
        },
    });
