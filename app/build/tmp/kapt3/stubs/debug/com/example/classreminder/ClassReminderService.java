package com.example.classreminder;

@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000J\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u000e\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\b\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0002\n\u0002\b\u0004\n\u0002\u0010\t\n\u0000\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u000b\u0018\u0000 $2\u00020\u0001:\u0001$B\u0005\u00a2\u0006\u0002\u0010\u0002J\u000e\u0010\u0010\u001a\u00020\u0011H\u0082@\u00a2\u0006\u0002\u0010\u0012J\b\u0010\u0013\u001a\u00020\u0011H\u0002J\u0010\u0010\u0014\u001a\u00020\u00042\u0006\u0010\u0015\u001a\u00020\u0016H\u0002J\u0014\u0010\u0017\u001a\u0004\u0018\u00010\u00182\b\u0010\u0019\u001a\u0004\u0018\u00010\u001aH\u0016J\b\u0010\u001b\u001a\u00020\u0011H\u0016J\b\u0010\u001c\u001a\u00020\u0011H\u0016J\"\u0010\u001d\u001a\u00020\b2\b\u0010\u0019\u001a\u0004\u0018\u00010\u001a2\u0006\u0010\u001e\u001a\u00020\b2\u0006\u0010\u001f\u001a\u00020\bH\u0016J\u0018\u0010 \u001a\u00020\u00112\u0006\u0010!\u001a\u00020\u00042\u0006\u0010\"\u001a\u00020\u0004H\u0002J\u0018\u0010#\u001a\u00020\u00112\u0006\u0010!\u001a\u00020\u00042\u0006\u0010\"\u001a\u00020\u0004H\u0002R\u000e\u0010\u0003\u001a\u00020\u0004X\u0082D\u00a2\u0006\u0002\n\u0000R\u0014\u0010\u0005\u001a\b\u0012\u0002\b\u0003\u0018\u00010\u0006X\u0082\u000e\u00a2\u0006\u0002\n\u0000R\u0012\u0010\u0007\u001a\u0004\u0018\u00010\bX\u0082\u000e\u00a2\u0006\u0004\n\u0002\u0010\tR\u000e\u0010\n\u001a\u00020\u0004X\u0082D\u00a2\u0006\u0002\n\u0000R\u0016\u0010\u000b\u001a\n \r*\u0004\u0018\u00010\f0\fX\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u000e\u0010\u000e\u001a\u00020\u000fX\u0082\u0004\u00a2\u0006\u0002\n\u0000\u00a8\u0006%"}, d2 = {"Lcom/example/classreminder/ClassReminderService;", "Landroid/app/Service;", "()V", "channelId", "", "future", "Ljava/util/concurrent/ScheduledFuture;", "lastShownId", "", "Ljava/lang/Integer;", "notifChannelId", "scheduler", "Ljava/util/concurrent/ScheduledExecutorService;", "kotlin.jvm.PlatformType", "serviceScope", "Lkotlinx/coroutines/CoroutineScope;", "checkAndNotify", "", "(Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "createChannels", "formatTime", "millis", "", "onBind", "Landroid/os/IBinder;", "intent", "Landroid/content/Intent;", "onCreate", "onDestroy", "onStartCommand", "flags", "startId", "startForegroundServiceWithNotification", "title", "content", "updateForegroundNotification", "Companion", "app_debug"})
public final class ClassReminderService extends android.app.Service {
    @org.jetbrains.annotations.NotNull
    public static final java.lang.String ACTION_CHECK_NOW = "com.example.classreminder.action.CHECK_NOW";
    @org.jetbrains.annotations.NotNull
    private final java.lang.String channelId = "class_reminder_service";
    @org.jetbrains.annotations.NotNull
    private final java.lang.String notifChannelId = "class_reminder_alerts";
    private final java.util.concurrent.ScheduledExecutorService scheduler = null;
    @org.jetbrains.annotations.Nullable
    private java.util.concurrent.ScheduledFuture<?> future;
    @org.jetbrains.annotations.NotNull
    private final kotlinx.coroutines.CoroutineScope serviceScope = null;
    @org.jetbrains.annotations.Nullable
    private java.lang.Integer lastShownId;
    @org.jetbrains.annotations.NotNull
    public static final com.example.classreminder.ClassReminderService.Companion Companion = null;
    
    public ClassReminderService() {
        super();
    }
    
    @java.lang.Override
    public void onCreate() {
    }
    
    private final void createChannels() {
    }
    
    private final void startForegroundServiceWithNotification(java.lang.String title, java.lang.String content) {
    }
    
    private final void updateForegroundNotification(java.lang.String title, java.lang.String content) {
    }
    
    private final java.lang.Object checkAndNotify(kotlin.coroutines.Continuation<? super kotlin.Unit> $completion) {
        return null;
    }
    
    private final java.lang.String formatTime(long millis) {
        return null;
    }
    
    @java.lang.Override
    @org.jetbrains.annotations.Nullable
    public android.os.IBinder onBind(@org.jetbrains.annotations.Nullable
    android.content.Intent intent) {
        return null;
    }
    
    @java.lang.Override
    public void onDestroy() {
    }
    
    @java.lang.Override
    public int onStartCommand(@org.jetbrains.annotations.Nullable
    android.content.Intent intent, int flags, int startId) {
        return 0;
    }
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000\u0012\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010\u000e\n\u0000\b\u0086\u0003\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002R\u000e\u0010\u0003\u001a\u00020\u0004X\u0086T\u00a2\u0006\u0002\n\u0000\u00a8\u0006\u0005"}, d2 = {"Lcom/example/classreminder/ClassReminderService$Companion;", "", "()V", "ACTION_CHECK_NOW", "", "app_debug"})
    public static final class Companion {
        
        private Companion() {
            super();
        }
    }
}