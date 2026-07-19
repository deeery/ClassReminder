package com.example.classreminder.data;

@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u00006\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\u0010 \n\u0002\u0018\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0003\n\u0002\u0018\u0002\n\u0000\n\u0002\u0010\u0002\n\u0002\b\u0004\u0018\u00002\u00020\u0001B\r\u0012\u0006\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\u0002\u0010\u0004J\u000e\u0010\u000f\u001a\u00020\u00102\u0006\u0010\u0011\u001a\u00020\bJ\b\u0010\u0012\u001a\u00020\u0010H\u0002J\u000e\u0010\u0013\u001a\u00020\u00102\u0006\u0010\u0011\u001a\u00020\bR\u001a\u0010\u0005\u001a\u000e\u0012\n\u0012\b\u0012\u0004\u0012\u00020\b0\u00070\u0006X\u0082\u0004\u00a2\u0006\u0002\n\u0000R\u001d\u0010\t\u001a\u000e\u0012\n\u0012\b\u0012\u0004\u0012\u00020\b0\u00070\n\u00a2\u0006\b\n\u0000\u001a\u0004\b\u000b\u0010\fR\u000e\u0010\r\u001a\u00020\u000eX\u0082\u0004\u00a2\u0006\u0002\n\u0000\u00a8\u0006\u0014"}, d2 = {"Lcom/example/classreminder/data/MainViewModel;", "Landroidx/lifecycle/AndroidViewModel;", "application", "Landroid/app/Application;", "(Landroid/app/Application;)V", "_classes", "Lkotlinx/coroutines/flow/MutableStateFlow;", "", "Lcom/example/classreminder/data/ClassEntity;", "classes", "Lkotlinx/coroutines/flow/StateFlow;", "getClasses", "()Lkotlinx/coroutines/flow/StateFlow;", "dao", "Lcom/example/classreminder/data/ClassDao;", "delete", "", "entity", "loadClasses", "save", "app_debug"})
public final class MainViewModel extends androidx.lifecycle.AndroidViewModel {
    @org.jetbrains.annotations.NotNull
    private final com.example.classreminder.data.ClassDao dao = null;
    @org.jetbrains.annotations.NotNull
    private final kotlinx.coroutines.flow.MutableStateFlow<java.util.List<com.example.classreminder.data.ClassEntity>> _classes = null;
    @org.jetbrains.annotations.NotNull
    private final kotlinx.coroutines.flow.StateFlow<java.util.List<com.example.classreminder.data.ClassEntity>> classes = null;
    
    public MainViewModel(@org.jetbrains.annotations.NotNull
    android.app.Application application) {
        super(null);
    }
    
    @org.jetbrains.annotations.NotNull
    public final kotlinx.coroutines.flow.StateFlow<java.util.List<com.example.classreminder.data.ClassEntity>> getClasses() {
        return null;
    }
    
    private final void loadClasses() {
    }
    
    /**
     * Insert a new class or replace an existing one (by id).
     */
    public final void save(@org.jetbrains.annotations.NotNull
    com.example.classreminder.data.ClassEntity entity) {
    }
    
    /**
     * Delete a class.
     */
    public final void delete(@org.jetbrains.annotations.NotNull
    com.example.classreminder.data.ClassEntity entity) {
    }
}