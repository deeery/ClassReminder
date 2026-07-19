package com.example.classreminder.ui;

@kotlin.Metadata(mv = {1, 9, 0}, k = 2, xi = 48, d1 = {"\u0000$\n\u0000\n\u0002\u0010\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0005\u001a6\u0010\u0000\u001a\u00020\u00012\n\b\u0002\u0010\u0002\u001a\u0004\u0018\u00010\u00032\u0012\u0010\u0004\u001a\u000e\u0012\u0004\u0012\u00020\u0003\u0012\u0004\u0012\u00020\u00010\u00052\f\u0010\u0006\u001a\b\u0012\u0004\u0012\u00020\u00010\u0007H\u0007\u001a:\u0010\b\u001a\u00020\u00012\u0006\u0010\t\u001a\u00020\n2\f\u0010\u000b\u001a\b\u0012\u0004\u0012\u00020\u00010\u00072\f\u0010\f\u001a\b\u0012\u0004\u0012\u00020\u00010\u00072\f\u0010\r\u001a\b\u0012\u0004\u0012\u00020\u00010\u0007H\u0007\u001a2\u0010\u000e\u001a\u00020\u00012\f\u0010\u0006\u001a\b\u0012\u0004\u0012\u00020\u00010\u00072\f\u0010\f\u001a\b\u0012\u0004\u0012\u00020\u00010\u00072\f\u0010\r\u001a\b\u0012\u0004\u0012\u00020\u00010\u0007H\u0007\u00a8\u0006\u000f"}, d2 = {"AddEditDialog", "", "initial", "Lcom/example/classreminder/data/ClassEntity;", "onSave", "Lkotlin/Function1;", "onDismiss", "Lkotlin/Function0;", "MainScreen", "viewModel", "Lcom/example/classreminder/data/MainViewModel;", "onStartMonitoringRequested", "onRequestNotificationPermission", "onOpenSettings", "SettingsDialog", "app_debug"})
public final class MainScreenKt {
    
    @androidx.compose.runtime.Composable
    public static final void MainScreen(@org.jetbrains.annotations.NotNull
    com.example.classreminder.data.MainViewModel viewModel, @org.jetbrains.annotations.NotNull
    kotlin.jvm.functions.Function0<kotlin.Unit> onStartMonitoringRequested, @org.jetbrains.annotations.NotNull
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
    
    @androidx.compose.runtime.Composable
    public static final void SettingsDialog(@org.jetbrains.annotations.NotNull
    kotlin.jvm.functions.Function0<kotlin.Unit> onDismiss, @org.jetbrains.annotations.NotNull
    kotlin.jvm.functions.Function0<kotlin.Unit> onRequestNotificationPermission, @org.jetbrains.annotations.NotNull
    kotlin.jvm.functions.Function0<kotlin.Unit> onOpenSettings) {
    }
}