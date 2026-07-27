package com.portalphotos.app.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

val Context.dataStore by preferencesDataStore(name = "app_preferences")

enum class ScalingMode { FIT, FILL_CENTER, FILL_SMART_CROP }
enum class SoundMode { MUTED, UNMUTED, REMEMBER_LAST }
enum class TransitionEffect { CROSSFADE, SLIDE, KEN_BURNS }
enum class ClockOverlayMode { HIDDEN, TOP_RIGHT, BOTTOM_LEFT, BAR }
enum class VideoCompletionMode { ADVANCE_ON_FINISH, CAP_30S, CAP_60S }
enum class LivePhotoBehavior { STILL_PHOTO_WITH_MOTION_TOGGLE, PLAY_MOTION_ONCE, STILL_PHOTO_ONLY, PLAY_AS_VIDEO }

data class UserSettings(
    val scalingMode: ScalingMode = ScalingMode.FILL_SMART_CROP,
    val autoplayVideos: Boolean = true,
    val soundMode: SoundMode = SoundMode.MUTED,
    val videoCompletionMode: VideoCompletionMode = VideoCompletionMode.ADVANCE_ON_FINISH,
    val livePhotoBehavior: LivePhotoBehavior = LivePhotoBehavior.STILL_PHOTO_WITH_MOTION_TOGGLE,
    val slideshowTimerSeconds: Int = 10,
    val shuffleMode: Boolean = false,
    val transitionEffect: TransitionEffect = TransitionEffect.CROSSFADE,
    val clockOverlayMode: ClockOverlayMode = ClockOverlayMode.TOP_RIGHT,
    val keepScreenAwake: Boolean = true,
    val pixelShiftProtection: Boolean = true,
    val preloadDepth: Int = 5,
    val showProgressBar: Boolean = false,
    val sleepScheduleEnabled: Boolean = true,
    val sleepStartHour: Int = 23,
    val sleepEndHour: Int = 8,
    val showAmbientCaption: Boolean = false
)

class AppPreferences(private val context: Context) {

    private object PreferenceKeys {
        val SCALING_MODE = stringPreferencesKey("scaling_mode")
        val AUTOPLAY_VIDEOS = booleanPreferencesKey("autoplay_videos")
        val SOUND_MODE = stringPreferencesKey("sound_mode")
        val VIDEO_COMPLETION = stringPreferencesKey("video_completion")
        val LIVE_PHOTO_BEHAVIOR = stringPreferencesKey("live_photo_behavior")
        val SLIDESHOW_TIMER = intPreferencesKey("slideshow_timer")
        val SHUFFLE_MODE = booleanPreferencesKey("shuffle_mode")
        val TRANSITION_EFFECT = stringPreferencesKey("transition_effect")
        val CLOCK_OVERLAY = stringPreferencesKey("clock_overlay")
        val KEEP_SCREEN_AWAKE = booleanPreferencesKey("keep_screen_awake")
        val PIXEL_SHIFT = booleanPreferencesKey("pixel_shift")
        val PRELOAD_DEPTH = intPreferencesKey("preload_depth")
        val SHOW_PROGRESS_BAR = booleanPreferencesKey("show_progress_bar")
        val SLEEP_SCHEDULE_ENABLED = booleanPreferencesKey("sleep_schedule_enabled")
        val SLEEP_START_HOUR = intPreferencesKey("sleep_start_hour")
        val SLEEP_END_HOUR = intPreferencesKey("sleep_end_hour")
        val SHOW_AMBIENT_CAPTION = booleanPreferencesKey("show_ambient_caption")
    }

    val userSettingsFlow: Flow<UserSettings> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { prefs ->
            UserSettings(
                scalingMode = prefs[PreferenceKeys.SCALING_MODE]?.let { runCatching { ScalingMode.valueOf(it) }.getOrNull() } ?: ScalingMode.FILL_SMART_CROP,
                autoplayVideos = prefs[PreferenceKeys.AUTOPLAY_VIDEOS] ?: true,
                soundMode = prefs[PreferenceKeys.SOUND_MODE]?.let { runCatching { SoundMode.valueOf(it) }.getOrNull() } ?: SoundMode.MUTED,
                videoCompletionMode = prefs[PreferenceKeys.VIDEO_COMPLETION]?.let { runCatching { VideoCompletionMode.valueOf(it) }.getOrNull() } ?: VideoCompletionMode.ADVANCE_ON_FINISH,
                livePhotoBehavior = prefs[PreferenceKeys.LIVE_PHOTO_BEHAVIOR]?.let { runCatching { LivePhotoBehavior.valueOf(it) }.getOrNull() } ?: LivePhotoBehavior.STILL_PHOTO_WITH_MOTION_TOGGLE,
                slideshowTimerSeconds = prefs[PreferenceKeys.SLIDESHOW_TIMER] ?: 10,
                shuffleMode = prefs[PreferenceKeys.SHUFFLE_MODE] ?: false,
                transitionEffect = prefs[PreferenceKeys.TRANSITION_EFFECT]?.let { runCatching { TransitionEffect.valueOf(it) }.getOrNull() } ?: TransitionEffect.CROSSFADE,
                clockOverlayMode = prefs[PreferenceKeys.CLOCK_OVERLAY]?.let { runCatching { ClockOverlayMode.valueOf(it) }.getOrNull() } ?: ClockOverlayMode.TOP_RIGHT,
                keepScreenAwake = prefs[PreferenceKeys.KEEP_SCREEN_AWAKE] ?: true,
                pixelShiftProtection = prefs[PreferenceKeys.PIXEL_SHIFT] ?: true,
                preloadDepth = prefs[PreferenceKeys.PRELOAD_DEPTH] ?: 5,
                showProgressBar = prefs[PreferenceKeys.SHOW_PROGRESS_BAR] ?: false,
                sleepScheduleEnabled = prefs[PreferenceKeys.SLEEP_SCHEDULE_ENABLED] ?: true,
                sleepStartHour = prefs[PreferenceKeys.SLEEP_START_HOUR] ?: 23,
                sleepEndHour = prefs[PreferenceKeys.SLEEP_END_HOUR] ?: 8,
                showAmbientCaption = prefs[PreferenceKeys.SHOW_AMBIENT_CAPTION] ?: false
            )
        }

    suspend fun updateScalingMode(mode: ScalingMode) {
        context.dataStore.edit { it[PreferenceKeys.SCALING_MODE] = mode.name }
    }

    suspend fun updateAutoplayVideos(enabled: Boolean) {
        context.dataStore.edit { it[PreferenceKeys.AUTOPLAY_VIDEOS] = enabled }
    }

    suspend fun updateSoundMode(mode: SoundMode) {
        context.dataStore.edit { it[PreferenceKeys.SOUND_MODE] = mode.name }
    }

    suspend fun updateVideoCompletionMode(mode: VideoCompletionMode) {
        context.dataStore.edit { it[PreferenceKeys.VIDEO_COMPLETION] = mode.name }
    }

    suspend fun updateLivePhotoBehavior(behavior: LivePhotoBehavior) {
        context.dataStore.edit { it[PreferenceKeys.LIVE_PHOTO_BEHAVIOR] = behavior.name }
    }

    suspend fun updateSlideshowTimer(seconds: Int) {
        context.dataStore.edit { it[PreferenceKeys.SLIDESHOW_TIMER] = seconds }
    }

    suspend fun updateShuffleMode(enabled: Boolean) {
        context.dataStore.edit { it[PreferenceKeys.SHUFFLE_MODE] = enabled }
    }

    suspend fun updateTransitionEffect(effect: TransitionEffect) {
        context.dataStore.edit { it[PreferenceKeys.TRANSITION_EFFECT] = effect.name }
    }

    suspend fun updateClockOverlay(mode: ClockOverlayMode) {
        context.dataStore.edit { it[PreferenceKeys.CLOCK_OVERLAY] = mode.name }
    }

    suspend fun updateKeepScreenAwake(enabled: Boolean) {
        context.dataStore.edit { it[PreferenceKeys.KEEP_SCREEN_AWAKE] = enabled }
    }

    suspend fun updatePixelShiftProtection(enabled: Boolean) {
        context.dataStore.edit { it[PreferenceKeys.PIXEL_SHIFT] = enabled }
    }

    suspend fun updatePreloadDepth(depth: Int) {
        context.dataStore.edit { it[PreferenceKeys.PRELOAD_DEPTH] = depth }
    }

    suspend fun updateShowProgressBar(enabled: Boolean) {
        context.dataStore.edit { it[PreferenceKeys.SHOW_PROGRESS_BAR] = enabled }
    }

    suspend fun updateSleepScheduleEnabled(enabled: Boolean) {
        context.dataStore.edit { it[PreferenceKeys.SLEEP_SCHEDULE_ENABLED] = enabled }
    }

    suspend fun updateSleepStartHour(startHour: Int) {
        context.dataStore.edit { it[PreferenceKeys.SLEEP_START_HOUR] = startHour }
    }

    suspend fun updateSleepEndHour(endHour: Int) {
        context.dataStore.edit { it[PreferenceKeys.SLEEP_END_HOUR] = endHour }
    }

    suspend fun updateShowAmbientCaption(enabled: Boolean) {
        context.dataStore.edit { it[PreferenceKeys.SHOW_AMBIENT_CAPTION] = enabled }
    }
}
