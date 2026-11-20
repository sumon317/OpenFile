package com.example.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.core.model.SortField
import com.example.core.model.SortOrder
import com.example.core.model.ViewMode
import com.example.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.openFileDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "openfile_settings"
)

data class AppSettingsState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val useDynamicColor: Boolean = false,
    val defaultViewMode: ViewMode = ViewMode.LIST,
    val showThumbnails: Boolean = true,
    val showHiddenFiles: Boolean = false,
    val showFileExtensions: Boolean = true,
    val confirmBeforeDelete: Boolean = true,
    val moveToTrashByDefault: Boolean = true,
    val verifyChecksumAfterTransfer: Boolean = false,
    val defaultSortField: SortField = SortField.NAME,
    val defaultSortOrder: SortOrder = SortOrder.ASCENDING,
    val largeFileThresholdMb: Int = 25,
    // FTP Server Preferences
    val ftpPort: Int = 2121,
    val ftpPassivePortStart: Int = 50000,
    val ftpPassivePortEnd: Int = 50050,
    val ftpUsername: String = "openfile",
    val ftpSecretAlias: String = "",
    val ftpAnonymousAllowed: Boolean = false,
    val ftpAllowWrite: Boolean = true,
    val ftpUseExplicitTls: Boolean = false,
    val ftpIdleTimeoutMinutes: Int = 15,
    val ftpRootPath: String = "",
    val autoCheckUpdates: Boolean = true,
    val fileOpenAction: FileOpenAction = FileOpenAction.BUILT_IN_FIRST,
    val musicPlayerSettings: MusicPlayerCustomSettings = MusicPlayerCustomSettings()
)

enum class ProgressBarPreset(val label: String, val description: String) {
    STANDARD("Classic Line", "Smooth standard line with rounded thumb"),
    WAVE_PILL("Pill Wave", "Modern rounded capsule track with elevated thumb"),
    SEGMENTED("Segmented", "Segmented dash ticks with precision marker"),
    MINIMAL_THIN("Minimal Thin", "Ultra-thin hairline line with micro indicator")
}

enum class PlayButtonShape(val label: String) {
    CIRCLE("Circle"),
    ROUNDED("Square / Rounded")
}

enum class PlayerThemePreset(
    val label: String,
    val backgroundStart: Long,
    val backgroundEnd: Long,
    val accentColor: Long,
    val cardSurface: Long
) {
    STUDIO_PURPLE("Studio Purple", 0xFF140D2B, 0xFF0A0714, 0xFF8B5CF6, 0xFF1E1736),
    OLED_BLACK("OLED Black", 0xFF000000, 0xFF050505, 0xFF6366F1, 0xFF111111),
    CYBERPUNK_NEON("Cyberpunk Neon", 0xFF080F1E, 0xFF03070E, 0xFF00E5FF, 0xFF0F1B2E),
    WARM_SUNSET("Warm Sunset", 0xFF1F0D15, 0xFF0E050A, 0xFFFF6B6B, 0xFF2D1520),
    OCEAN_DEPTHS("Ocean Depths", 0xFF071824, 0xFF030B12, 0xFF00B4D8, 0xFF0E2536),
    MINIMAL_MONOCHROME("Monochrome", 0xFF121214, 0xFF0A0A0B, 0xFFE0E0E0, 0xFF1E1E22)
}

enum class PlayerFontStyle(val label: String) {
    DEFAULT("Sans Serif"),
    MONOSPACE("Monospace (Retro)"),
    SERIF("Serif (Classic)"),
    CURSIVE("Cursive (Artistic)")
}

enum class PlayerFontSize(val label: String, val scaleFactor: Float) {
    COMPACT("Compact", 0.85f),
    STANDARD("Standard", 1.0f),
    LARGE("Large", 1.18f)
}

data class MusicPlayerCustomSettings(
    val albumArtCornerRadiusDp: Int = 24,
    val isAlbumArtCircle: Boolean = false,
    val rotateIfCircle: Boolean = false,
    val songTitleMarquee: Boolean = true,
    val progressBarStyle: ProgressBarPreset = ProgressBarPreset.STANDARD,
    val playButtonShape: PlayButtonShape = PlayButtonShape.CIRCLE,
    val playButtonCornerRadiusDp: Int = 36,
    val playerTheme: PlayerThemePreset = PlayerThemePreset.STUDIO_PURPLE,
    val fontStyle: PlayerFontStyle = PlayerFontStyle.DEFAULT,
    val fontSize: PlayerFontSize = PlayerFontSize.STANDARD
)

enum class FileOpenAction(val label: String, val description: String) {
    BUILT_IN_FIRST("Built-in Viewer First", "Opens files in OpenFile's built-in readers and players (media, documents, images)"),
    ASK_OPEN_WITH("Ask Every Time", "Shows the 'Open With...' bottom sheet to choose built-in or external app"),
    ALWAYS_EXTERNAL("Always Use System Chooser", "Always shows Android's system App Chooser to pick an external app")
}

class SettingsDataStore(private val context: Context) {

    private val themeCache = context.getSharedPreferences("openfile_theme_cache", Context.MODE_PRIVATE)

    fun getInitialThemeMode(): ThemeMode {
        val saved = themeCache.getString("theme_mode", null) ?: return ThemeMode.SYSTEM
        return runCatching { ThemeMode.valueOf(saved) }.getOrDefault(ThemeMode.SYSTEM)
    }

    fun getInitialDynamicColor(): Boolean {
        return themeCache.getBoolean("dynamic_color", false)
    }

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val VIEW_MODE = stringPreferencesKey("view_mode")
        val SHOW_THUMBNAILS = booleanPreferencesKey("show_thumbnails")
        val SHOW_HIDDEN = booleanPreferencesKey("show_hidden")
        val SHOW_EXTENSIONS = booleanPreferencesKey("show_extensions")
        val CONFIRM_DELETE = booleanPreferencesKey("confirm_delete")
        val USE_TRASH = booleanPreferencesKey("use_trash")
        val VERIFY_CHECKSUM = booleanPreferencesKey("verify_checksum")
        val SORT_FIELD = stringPreferencesKey("sort_field")
        val SORT_ORDER = stringPreferencesKey("sort_order")
        val LARGE_FILE_MB = intPreferencesKey("large_file_mb")

        val FTP_PORT = intPreferencesKey("ftp_port")
        val FTP_PASV_START = intPreferencesKey("ftp_pasv_start")
        val FTP_PASV_END = intPreferencesKey("ftp_pasv_end")
        val FTP_USERNAME = stringPreferencesKey("ftp_username")
        val FTP_SECRET_ALIAS = stringPreferencesKey("ftp_secret_alias")
        val FTP_ANONYMOUS = booleanPreferencesKey("ftp_anonymous")
        val FTP_ALLOW_WRITE = booleanPreferencesKey("ftp_allow_write")
        val FTP_USE_TLS = booleanPreferencesKey("ftp_use_tls")
        val FTP_TIMEOUT_MIN = intPreferencesKey("ftp_timeout_min")
        val FTP_ROOT_PATH = stringPreferencesKey("ftp_root_path")
        val AUTO_CHECK_UPDATES = booleanPreferencesKey("auto_check_updates")
        val FILE_OPEN_ACTION = stringPreferencesKey("file_open_action")

        // Custom Music Player & Audio Studio Preferences
        val MUSIC_ART_RADIUS = intPreferencesKey("music_art_radius")
        val MUSIC_ART_IS_CIRCLE = booleanPreferencesKey("music_art_is_circle")
        val MUSIC_ART_ROTATE_CIRCLE = booleanPreferencesKey("music_art_rotate_circle")
        val MUSIC_TITLE_MARQUEE = booleanPreferencesKey("music_title_marquee")
        val MUSIC_PROGRESS_STYLE = stringPreferencesKey("music_progress_style")
        val MUSIC_PLAY_BUTTON_SHAPE = stringPreferencesKey("music_play_button_shape")
        val MUSIC_PLAY_BUTTON_RADIUS = intPreferencesKey("music_play_button_radius")
        val MUSIC_PLAYER_THEME = stringPreferencesKey("music_player_theme")
        val MUSIC_FONT_STYLE = stringPreferencesKey("music_font_style")
        val MUSIC_FONT_SIZE = stringPreferencesKey("music_font_size")
    }

    val settingsFlow: Flow<AppSettingsState> = context.openFileDataStore.data.map { prefs ->
        val resolvedThemeMode = runCatching {
            ThemeMode.valueOf(prefs[Keys.THEME_MODE] ?: ThemeMode.SYSTEM.name)
        }.getOrDefault(ThemeMode.SYSTEM)
        val resolvedDynamicColor = prefs[Keys.DYNAMIC_COLOR] ?: false
        themeCache.edit()
            .putString("theme_mode", resolvedThemeMode.name)
            .putBoolean("dynamic_color", resolvedDynamicColor)
            .apply()

        val musicSettings = MusicPlayerCustomSettings(
            albumArtCornerRadiusDp = prefs[Keys.MUSIC_ART_RADIUS] ?: 24,
            isAlbumArtCircle = prefs[Keys.MUSIC_ART_IS_CIRCLE] ?: false,
            rotateIfCircle = prefs[Keys.MUSIC_ART_ROTATE_CIRCLE] ?: false,
            songTitleMarquee = prefs[Keys.MUSIC_TITLE_MARQUEE] ?: true,
            progressBarStyle = runCatching {
                ProgressBarPreset.valueOf(prefs[Keys.MUSIC_PROGRESS_STYLE] ?: ProgressBarPreset.STANDARD.name)
            }.getOrDefault(ProgressBarPreset.STANDARD),
            playButtonShape = runCatching {
                PlayButtonShape.valueOf(prefs[Keys.MUSIC_PLAY_BUTTON_SHAPE] ?: PlayButtonShape.CIRCLE.name)
            }.getOrDefault(PlayButtonShape.CIRCLE),
            playButtonCornerRadiusDp = prefs[Keys.MUSIC_PLAY_BUTTON_RADIUS] ?: 36,
            playerTheme = runCatching {
                PlayerThemePreset.valueOf(prefs[Keys.MUSIC_PLAYER_THEME] ?: PlayerThemePreset.STUDIO_PURPLE.name)
            }.getOrDefault(PlayerThemePreset.STUDIO_PURPLE),
            fontStyle = runCatching {
                PlayerFontStyle.valueOf(prefs[Keys.MUSIC_FONT_STYLE] ?: PlayerFontStyle.DEFAULT.name)
            }.getOrDefault(PlayerFontStyle.DEFAULT),
            fontSize = runCatching {
                PlayerFontSize.valueOf(prefs[Keys.MUSIC_FONT_SIZE] ?: PlayerFontSize.STANDARD.name)
            }.getOrDefault(PlayerFontSize.STANDARD)
        )

        AppSettingsState(
            themeMode = resolvedThemeMode,
            useDynamicColor = resolvedDynamicColor,
            defaultViewMode = runCatching {
                ViewMode.valueOf(prefs[Keys.VIEW_MODE] ?: ViewMode.LIST.name)
            }.getOrDefault(ViewMode.LIST),
            showThumbnails = prefs[Keys.SHOW_THUMBNAILS] ?: true,
            showHiddenFiles = prefs[Keys.SHOW_HIDDEN] ?: false,
            showFileExtensions = prefs[Keys.SHOW_EXTENSIONS] ?: true,
            confirmBeforeDelete = prefs[Keys.CONFIRM_DELETE] ?: true,
            moveToTrashByDefault = prefs[Keys.USE_TRASH] ?: true,
            verifyChecksumAfterTransfer = prefs[Keys.VERIFY_CHECKSUM] ?: false,
            defaultSortField = runCatching {
                SortField.valueOf(prefs[Keys.SORT_FIELD] ?: SortField.NAME.name)
            }.getOrDefault(SortField.NAME),
            defaultSortOrder = runCatching {
                SortOrder.valueOf(prefs[Keys.SORT_ORDER] ?: SortOrder.ASCENDING.name)
            }.getOrDefault(SortOrder.ASCENDING),
            largeFileThresholdMb = prefs[Keys.LARGE_FILE_MB] ?: 25,
            ftpPort = prefs[Keys.FTP_PORT] ?: 2121,
            ftpPassivePortStart = prefs[Keys.FTP_PASV_START] ?: 50000,
            ftpPassivePortEnd = prefs[Keys.FTP_PASV_END] ?: 50050,
            ftpUsername = prefs[Keys.FTP_USERNAME] ?: "openfile",
            ftpSecretAlias = prefs[Keys.FTP_SECRET_ALIAS] ?: "",
            ftpAnonymousAllowed = prefs[Keys.FTP_ANONYMOUS] ?: false,
            ftpAllowWrite = prefs[Keys.FTP_ALLOW_WRITE] ?: true,
            ftpUseExplicitTls = prefs[Keys.FTP_USE_TLS] ?: false,
            ftpIdleTimeoutMinutes = prefs[Keys.FTP_TIMEOUT_MIN] ?: 15,
            ftpRootPath = prefs[Keys.FTP_ROOT_PATH] ?: "",
            autoCheckUpdates = prefs[Keys.AUTO_CHECK_UPDATES] ?: true,
            fileOpenAction = runCatching {
                FileOpenAction.valueOf(prefs[Keys.FILE_OPEN_ACTION] ?: FileOpenAction.BUILT_IN_FIRST.name)
            }.getOrDefault(FileOpenAction.BUILT_IN_FIRST),
            musicPlayerSettings = musicSettings
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        themeCache.edit().putString("theme_mode", mode.name).apply()
        context.openFileDataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        themeCache.edit().putBoolean("dynamic_color", enabled).apply()
        context.openFileDataStore.edit { it[Keys.DYNAMIC_COLOR] = enabled }
    }

    suspend fun setViewMode(mode: ViewMode) {
        context.openFileDataStore.edit { it[Keys.VIEW_MODE] = mode.name }
    }

    suspend fun setShowThumbnails(enabled: Boolean) {
        context.openFileDataStore.edit { it[Keys.SHOW_THUMBNAILS] = enabled }
    }

    suspend fun setShowHiddenFiles(enabled: Boolean) {
        context.openFileDataStore.edit { it[Keys.SHOW_HIDDEN] = enabled }
    }

    suspend fun setShowFileExtensions(enabled: Boolean) {
        context.openFileDataStore.edit { it[Keys.SHOW_EXTENSIONS] = enabled }
    }

    suspend fun setConfirmBeforeDelete(enabled: Boolean) {
        context.openFileDataStore.edit { it[Keys.CONFIRM_DELETE] = enabled }
    }

    suspend fun setMoveToTrashByDefault(enabled: Boolean) {
        context.openFileDataStore.edit { it[Keys.USE_TRASH] = enabled }
    }

    suspend fun setVerifyChecksumAfterTransfer(enabled: Boolean) {
        context.openFileDataStore.edit { it[Keys.VERIFY_CHECKSUM] = enabled }
    }

    suspend fun setSorting(field: SortField, order: SortOrder) {
        context.openFileDataStore.edit {
            it[Keys.SORT_FIELD] = field.name
            it[Keys.SORT_ORDER] = order.name
        }
    }

    suspend fun setLargeFileThresholdMb(mb: Int) {
        context.openFileDataStore.edit { it[Keys.LARGE_FILE_MB] = mb.coerceIn(1, 5000) }
    }

    suspend fun updateFtpServerConfig(
        port: Int,
        passiveStart: Int,
        passiveEnd: Int,
        username: String,
        secretAlias: String,
        anonymousAllowed: Boolean,
        allowWrite: Boolean,
        useTls: Boolean,
        idleTimeoutMinutes: Int,
        rootPath: String
    ) {
        context.openFileDataStore.edit {
            it[Keys.FTP_PORT] = port.coerceIn(1024, 65535)
            it[Keys.FTP_PASV_START] = passiveStart.coerceIn(1025, 65500)
            it[Keys.FTP_PASV_END] = passiveEnd.coerceIn(passiveStart + 1, 65535)
            it[Keys.FTP_USERNAME] = username.ifBlank { "openfile" }
            it[Keys.FTP_SECRET_ALIAS] = secretAlias
            it[Keys.FTP_ANONYMOUS] = anonymousAllowed
            it[Keys.FTP_ALLOW_WRITE] = allowWrite
            it[Keys.FTP_USE_TLS] = useTls
            it[Keys.FTP_TIMEOUT_MIN] = idleTimeoutMinutes.coerceIn(1, 240)
            it[Keys.FTP_ROOT_PATH] = rootPath
        }
    }

    suspend fun setAutoCheckUpdates(enabled: Boolean) {
        context.openFileDataStore.edit { it[Keys.AUTO_CHECK_UPDATES] = enabled }
    }

    suspend fun setFileOpenAction(action: FileOpenAction) {
        context.openFileDataStore.edit { it[Keys.FILE_OPEN_ACTION] = action.name }
    }

    suspend fun updateMusicPlayerSettings(transform: (MusicPlayerCustomSettings) -> MusicPlayerCustomSettings) {
        context.openFileDataStore.edit { prefs ->
            val current = MusicPlayerCustomSettings(
                albumArtCornerRadiusDp = prefs[Keys.MUSIC_ART_RADIUS] ?: 24,
                isAlbumArtCircle = prefs[Keys.MUSIC_ART_IS_CIRCLE] ?: false,
                rotateIfCircle = prefs[Keys.MUSIC_ART_ROTATE_CIRCLE] ?: false,
                songTitleMarquee = prefs[Keys.MUSIC_TITLE_MARQUEE] ?: true,
                progressBarStyle = runCatching {
                    ProgressBarPreset.valueOf(prefs[Keys.MUSIC_PROGRESS_STYLE] ?: ProgressBarPreset.STANDARD.name)
                }.getOrDefault(ProgressBarPreset.STANDARD),
                playButtonShape = runCatching {
                    PlayButtonShape.valueOf(prefs[Keys.MUSIC_PLAY_BUTTON_SHAPE] ?: PlayButtonShape.CIRCLE.name)
                }.getOrDefault(PlayButtonShape.CIRCLE),
                playButtonCornerRadiusDp = prefs[Keys.MUSIC_PLAY_BUTTON_RADIUS] ?: 36,
                playerTheme = runCatching {
                    PlayerThemePreset.valueOf(prefs[Keys.MUSIC_PLAYER_THEME] ?: PlayerThemePreset.STUDIO_PURPLE.name)
                }.getOrDefault(PlayerThemePreset.STUDIO_PURPLE),
                fontStyle = runCatching {
                    PlayerFontStyle.valueOf(prefs[Keys.MUSIC_FONT_STYLE] ?: PlayerFontStyle.DEFAULT.name)
                }.getOrDefault(PlayerFontStyle.DEFAULT),
                fontSize = runCatching {
                    PlayerFontSize.valueOf(prefs[Keys.MUSIC_FONT_SIZE] ?: PlayerFontSize.STANDARD.name)
                }.getOrDefault(PlayerFontSize.STANDARD)
            )
            val updated = transform(current)
            prefs[Keys.MUSIC_ART_RADIUS] = updated.albumArtCornerRadiusDp.coerceIn(0, 48)
            prefs[Keys.MUSIC_ART_IS_CIRCLE] = updated.isAlbumArtCircle
            prefs[Keys.MUSIC_ART_ROTATE_CIRCLE] = if (updated.isAlbumArtCircle) updated.rotateIfCircle else false
            prefs[Keys.MUSIC_TITLE_MARQUEE] = updated.songTitleMarquee
            prefs[Keys.MUSIC_PROGRESS_STYLE] = updated.progressBarStyle.name
            prefs[Keys.MUSIC_PLAY_BUTTON_SHAPE] = updated.playButtonShape.name
            prefs[Keys.MUSIC_PLAY_BUTTON_RADIUS] = updated.playButtonCornerRadiusDp.coerceIn(0, 36)
            prefs[Keys.MUSIC_PLAYER_THEME] = updated.playerTheme.name
            prefs[Keys.MUSIC_FONT_STYLE] = updated.fontStyle.name
            prefs[Keys.MUSIC_FONT_SIZE] = updated.fontSize.name
        }
    }
}
