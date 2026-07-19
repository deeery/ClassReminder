package com.example.classreminder.data;

@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000 \n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0010\u0002\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010 \n\u0002\b\u0003\bg\u0018\u00002\u00020\u0001J\u0016\u0010\u0002\u001a\u00020\u00032\u0006\u0010\u0004\u001a\u00020\u0005H\u00a7@\u00a2\u0006\u0002\u0010\u0006J\u0014\u0010\u0007\u001a\b\u0012\u0004\u0012\u00020\u00050\bH\u00a7@\u00a2\u0006\u0002\u0010\tJ\u0016\u0010\n\u001a\u00020\u00032\u0006\u0010\u0004\u001a\u00020\u0005H\u00a7@\u00a2\u0006\u0002\u0010\u0006\u00a8\u0006\u000b"}, d2 = {"Lcom/example/classreminder/data/ClassDao;", "", "delete", "", "entity", "Lcom/example/classreminder/data/ClassEntity;", "(Lcom/example/classreminder/data/ClassEntity;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "getAll", "", "(Lkotlin/coroutines/Continuation;)Ljava/lang/Object;", "insert", "app_debug"})
@androidx.room.Dao
public abstract interface ClassDao {
    
    @androidx.room.Query(value = "SELECT * FROM classes ORDER BY dayOfWeek, startTime")
    @org.jetbrains.annotations.Nullable
    public abstract java.lang.Object getAll(@org.jetbrains.annotations.NotNull
    kotlin.coroutines.Continuation<? super java.util.List<com.example.classreminder.data.ClassEntity>> $completion);
    
    @androidx.room.Insert(onConflict = 1)
    @org.jetbrains.annotations.Nullable
    public abstract java.lang.Object insert(@org.jetbrains.annotations.NotNull
    com.example.classreminder.data.ClassEntity entity, @org.jetbrains.annotations.NotNull
    kotlin.coroutines.Continuation<? super kotlin.Unit> $completion);
    
    @androidx.room.Delete
    @org.jetbrains.annotations.Nullable
    public abstract java.lang.Object delete(@org.jetbrains.annotations.NotNull
    com.example.classreminder.data.ClassEntity entity, @org.jetbrains.annotations.NotNull
    kotlin.coroutines.Continuation<? super kotlin.Unit> $completion);
}