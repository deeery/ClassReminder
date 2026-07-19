package com.example.classreminder;

@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u00000\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010\u000e\n\u0002\b\u0006\n\u0002\u0010\b\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u000b\n\u0002\b\u0004\n\u0002\u0010\u0002\n\u0002\b\t\b\u00c6\u0002\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002J\u000e\u0010\n\u001a\u00020\u000b2\u0006\u0010\f\u001a\u00020\rJ\u000e\u0010\u000e\u001a\u00020\u000f2\u0006\u0010\f\u001a\u00020\rJ\u000e\u0010\u0010\u001a\u00020\u000f2\u0006\u0010\f\u001a\u00020\rJ\u000e\u0010\u0011\u001a\u00020\u000b2\u0006\u0010\f\u001a\u00020\rJ\u000e\u0010\u0012\u001a\u00020\u000f2\u0006\u0010\f\u001a\u00020\rJ\u0016\u0010\u0013\u001a\u00020\u00142\u0006\u0010\f\u001a\u00020\r2\u0006\u0010\u0015\u001a\u00020\u000bJ\u0016\u0010\u0016\u001a\u00020\u00142\u0006\u0010\f\u001a\u00020\r2\u0006\u0010\u0017\u001a\u00020\u000fJ\u000e\u0010\u0018\u001a\u00020\u00142\u0006\u0010\f\u001a\u00020\rJ\u0016\u0010\u0019\u001a\u00020\u00142\u0006\u0010\f\u001a\u00020\r2\u0006\u0010\u001a\u001a\u00020\u000fJ\u0016\u0010\u001b\u001a\u00020\u00142\u0006\u0010\f\u001a\u00020\r2\u0006\u0010\u001c\u001a\u00020\u000bR\u000e\u0010\u0003\u001a\u00020\u0004X\u0082T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0005\u001a\u00020\u0004X\u0082T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0006\u001a\u00020\u0004X\u0082T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u0007\u001a\u00020\u0004X\u0082T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\b\u001a\u00020\u0004X\u0082T\u00a2\u0006\u0002\n\u0000R\u000e\u0010\t\u001a\u00020\u0004X\u0082T\u00a2\u0006\u0002\n\u0000\u00a8\u0006\u001d"}, d2 = {"Lcom/example/classreminder/Prefs;", "", "()V", "KEY_ADVANCE_MIN", "", "KEY_AUTO_START", "KEY_FIRST_RUN", "KEY_SHOW_POPUP", "KEY_THEME_MODE", "NAME", "getAdvanceMinutes", "", "ctx", "Landroid/content/Context;", "getAutoStart", "", "getShowPopup", "getThemeMode", "isFirstRun", "setAdvanceMinutes", "", "minutes", "setAutoStart", "enabled", "setFirstRunDone", "setShowPopup", "show", "setThemeMode", "mode", "app_debug"})
public final class Prefs {
    @org.jetbrains.annotations.NotNull
    private static final java.lang.String NAME = "classreminder_prefs";
    @org.jetbrains.annotations.NotNull
    private static final java.lang.String KEY_ADVANCE_MIN = "advance_minutes";
    @org.jetbrains.annotations.NotNull
    private static final java.lang.String KEY_AUTO_START = "auto_start";
    @org.jetbrains.annotations.NotNull
    private static final java.lang.String KEY_SHOW_POPUP = "show_popup";
    @org.jetbrains.annotations.NotNull
    private static final java.lang.String KEY_FIRST_RUN = "first_run";
    @org.jetbrains.annotations.NotNull
    private static final java.lang.String KEY_THEME_MODE = "theme_mode";
    @org.jetbrains.annotations.NotNull
    public static final com.example.classreminder.Prefs INSTANCE = null;
    
    private Prefs() {
        super();
    }
    
    public final int getAdvanceMinutes(@org.jetbrains.annotations.NotNull
    android.content.Context ctx) {
        return 0;
    }
    
    public final void setAdvanceMinutes(@org.jetbrains.annotations.NotNull
    android.content.Context ctx, int minutes) {
    }
    
    public final boolean getAutoStart(@org.jetbrains.annotations.NotNull
    android.content.Context ctx) {
        return false;
    }
    
    public final void setAutoStart(@org.jetbrains.annotations.NotNull
    android.content.Context ctx, boolean enabled) {
    }
    
    public final boolean getShowPopup(@org.jetbrains.annotations.NotNull
    android.content.Context ctx) {
        return false;
    }
    
    public final void setShowPopup(@org.jetbrains.annotations.NotNull
    android.content.Context ctx, boolean show) {
    }
    
    public final boolean isFirstRun(@org.jetbrains.annotations.NotNull
    android.content.Context ctx) {
        return false;
    }
    
    public final void setFirstRunDone(@org.jetbrains.annotations.NotNull
    android.content.Context ctx) {
    }
    
    public final int getThemeMode(@org.jetbrains.annotations.NotNull
    android.content.Context ctx) {
        return 0;
    }
    
    public final void setThemeMode(@org.jetbrains.annotations.NotNull
    android.content.Context ctx, int mode) {
    }
}