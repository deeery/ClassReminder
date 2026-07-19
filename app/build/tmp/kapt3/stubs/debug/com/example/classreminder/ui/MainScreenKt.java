package com.example.classreminder.ui;

@kotlin.Metadata(mv = {1, 9, 0}, k = 2, xi = 48, d1 = {"\u0000<\n\u0000\n\u0002\u0010$\n\u0002\u0010\u000e\n\u0000\n\u0002\u0010 \n\u0000\n\u0002\u0010\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\b\n\u0002\b\u0007\n\u0002\u0018\u0002\n\u0002\b\u0007\u001a6\u0010\u0005\u001a\u00020\u00062\n\b\u0002\u0010\u0007\u001a\u0004\u0018\u00010\b2\u0012\u0010\t\u001a\u000e\u0012\u0004\u0012\u00020\b\u0012\u0004\u0012\u00020\u00060\n2\f\u0010\u000b\u001a\b\u0012\u0004\u0012\u00020\u00060\fH\u0007\u001a$\u0010\r\u001a\u00020\u00062\u0006\u0010\u000e\u001a\u00020\u000f2\u0012\u0010\u0010\u001a\u000e\u0012\u0004\u0012\u00020\u000f\u0012\u0004\u0012\u00020\u00060\nH\u0007\u001a>\u0010\u0011\u001a\u00020\u00062\f\u0010\u0012\u001a\b\u0012\u0004\u0012\u00020\b0\u00042\u0012\u0010\u0013\u001a\u000e\u0012\u0004\u0012\u00020\b\u0012\u0004\u0012\u00020\u00060\n2\u0012\u0010\u0014\u001a\u000e\u0012\u0004\u0012\u00020\b\u0012\u0004\u0012\u00020\u00060\nH\u0007\u001aH\u0010\u0015\u001a\u00020\u00062\u0006\u0010\u0016\u001a\u00020\u00172\u0006\u0010\u0018\u001a\u00020\u000f2\u0012\u0010\u0019\u001a\u000e\u0012\u0004\u0012\u00020\u000f\u0012\u0004\u0012\u00020\u00060\n2\f\u0010\u001a\u001a\b\u0012\u0004\u0012\u00020\u00060\f2\f\u0010\u001b\u001a\b\u0012\u0004\u0012\u00020\u00060\fH\u0007\u001a@\u0010\u001c\u001a\u00020\u00062\u0006\u0010\u0018\u001a\u00020\u000f2\u0012\u0010\u0019\u001a\u000e\u0012\u0004\u0012\u00020\u000f\u0012\u0004\u0012\u00020\u00060\n2\f\u0010\u001a\u001a\b\u0012\u0004\u0012\u00020\u00060\f2\f\u0010\u001b\u001a\b\u0012\u0004\u0012\u00020\u00060\fH\u0007\u001a\u0016\u0010\u001d\u001a\u00020\u00062\f\u0010\u0012\u001a\b\u0012\u0004\u0012\u00020\b0\u0004H\u0007\"\u001a\u0010\u0000\u001a\u000e\u0012\u0004\u0012\u00020\u0002\u0012\u0004\u0012\u00020\u00020\u0001X\u0082\u0004\u00a2\u0006\u0002\n\u0000\"\u0014\u0010\u0003\u001a\b\u0012\u0004\u0012\u00020\u00020\u0004X\u0082\u0004\u00a2\u0006\u0002\n\u0000\u00a8\u0006\u001e"}, d2 = {"dayLabel", "", "", "dayOrder", "", "AddEditDialog", "", "initial", "Lcom/example/classreminder/data/ClassEntity;", "onSave", "Lkotlin/Function1;", "onDismiss", "Lkotlin/Function0;", "BottomNavigationBar", "selectedTab", "", "onTabSelected", "ClassListView", "classes", "onEdit", "onDelete", "MainScreen", "viewModel", "Lcom/example/classreminder/data/MainViewModel;", "themeMode", "onThemeModeChanged", "onRequestNotificationPermission", "onOpenSettings", "SettingsPage", "WeekView", "app_debug"})
public final class MainScreenKt {
    @org.jetbrains.annotations.NotNull
    private static final java.util.List<java.lang.String> dayOrder = null;
    @org.jetbrains.annotations.NotNull
    private static final java.util.Map<java.lang.String, java.lang.String> dayLabel = null;
    
    @androidx.compose.runtime.Composable
    public static final void MainScreen(@org.jetbrains.annotations.NotNull
    com.example.classreminder.data.MainViewModel viewModel, int themeMode, @org.jetbrains.annotations.NotNull
    kotlin.jvm.functions.Function1<? super java.lang.Integer, kotlin.Unit> onThemeModeChanged, @org.jetbrains.annotations.NotNull
    kotlin.jvm.functions.Function0<kotlin.Unit> onRequestNotificationPermission, @org.jetbrains.annotations.NotNull
    kotlin.jvm.functions.Function0<kotlin.Unit> onOpenSettings) {
    }
    
    @androidx.compose.runtime.Composable
    public static final void BottomNavigationBar(int selectedTab, @org.jetbrains.annotations.NotNull
    kotlin.jvm.functions.Function1<? super java.lang.Integer, kotlin.Unit> onTabSelected) {
    }
    
    @androidx.compose.runtime.Composable
    public static final void ClassListView(@org.jetbrains.annotations.NotNull
    java.util.List<com.example.classreminder.data.ClassEntity> classes, @org.jetbrains.annotations.NotNull
    kotlin.jvm.functions.Function1<? super com.example.classreminder.data.ClassEntity, kotlin.Unit> onEdit, @org.jetbrains.annotations.NotNull
    kotlin.jvm.functions.Function1<? super com.example.classreminder.data.ClassEntity, kotlin.Unit> onDelete) {
    }
    
    @androidx.compose.runtime.Composable
    public static final void WeekView(@org.jetbrains.annotations.NotNull
    java.util.List<com.example.classreminder.data.ClassEntity> classes) {
    }
    
    @androidx.compose.runtime.Composable
    public static final void SettingsPage(int themeMode, @org.jetbrains.annotations.NotNull
    kotlin.jvm.functions.Function1<? super java.lang.Integer, kotlin.Unit> onThemeModeChanged, @org.jetbrains.annotations.NotNull
    kotlin.jvm.functions.Function0<kotlin.Unit> onRequestNotificationPermission, @org.jetbrains.annotations.NotNull
    kotlin.jvm.functions.Function0<kotlin.Unit> onOpenSettings) {
    }
    
    @kotlin.OptIn(markerClass = {androidx.compose.material.ExperimentalMaterialApi.class})
    @androidx.compose.runtime.Composable
    public static final void AddEditDialog(@org.jetbrains.annotations.Nullable
    com.example.classreminder.data.ClassEntity initial, @org.jetbrains.annotations.NotNull
    kotlin.jvm.functions.Function1<? super com.example.classreminder.data.ClassEntity, kotlin.Unit> onSave, @org.jetbrains.annotations.NotNull
    kotlin.jvm.functions.Function0<kotlin.Unit> onDismiss) {
    }
}