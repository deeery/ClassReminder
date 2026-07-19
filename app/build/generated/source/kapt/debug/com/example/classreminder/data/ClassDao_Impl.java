package com.example.classreminder.data;

import android.database.Cursor;
import android.os.CancellationSignal;
import androidx.annotation.NonNull;
import androidx.room.CoroutinesRoom;
import androidx.room.EntityDeletionOrUpdateAdapter;
import androidx.room.EntityInsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomSQLiteQuery;
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import java.lang.Class;
import java.lang.Exception;
import java.lang.Object;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import kotlin.Unit;
import kotlin.coroutines.Continuation;

@SuppressWarnings({"unchecked", "deprecation"})
public final class ClassDao_Impl implements ClassDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<ClassEntity> __insertionAdapterOfClassEntity;

  private final EntityDeletionOrUpdateAdapter<ClassEntity> __deletionAdapterOfClassEntity;

  public ClassDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfClassEntity = new EntityInsertionAdapter<ClassEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `classes` (`id`,`title`,`dayOfWeek`,`startTime`,`endTime`,`room`,`notes`) VALUES (?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final ClassEntity entity) {
        statement.bindLong(1, entity.getId());
        if (entity.getTitle() == null) {
          statement.bindNull(2);
        } else {
          statement.bindString(2, entity.getTitle());
        }
        if (entity.getDayOfWeek() == null) {
          statement.bindNull(3);
        } else {
          statement.bindString(3, entity.getDayOfWeek());
        }
        if (entity.getStartTime() == null) {
          statement.bindNull(4);
        } else {
          statement.bindString(4, entity.getStartTime());
        }
        if (entity.getEndTime() == null) {
          statement.bindNull(5);
        } else {
          statement.bindString(5, entity.getEndTime());
        }
        if (entity.getRoom() == null) {
          statement.bindNull(6);
        } else {
          statement.bindString(6, entity.getRoom());
        }
        if (entity.getNotes() == null) {
          statement.bindNull(7);
        } else {
          statement.bindString(7, entity.getNotes());
        }
      }
    };
    this.__deletionAdapterOfClassEntity = new EntityDeletionOrUpdateAdapter<ClassEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "DELETE FROM `classes` WHERE `id` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final ClassEntity entity) {
        statement.bindLong(1, entity.getId());
      }
    };
  }

  @Override
  public Object insert(final ClassEntity entity, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfClassEntity.insert(entity);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object delete(final ClassEntity entity, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __deletionAdapterOfClassEntity.handle(entity);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object getAll(final Continuation<? super List<ClassEntity>> $completion) {
    final String _sql = "SELECT * FROM classes ORDER BY dayOfWeek, startTime";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<ClassEntity>>() {
      @Override
      @NonNull
      public List<ClassEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfTitle = CursorUtil.getColumnIndexOrThrow(_cursor, "title");
          final int _cursorIndexOfDayOfWeek = CursorUtil.getColumnIndexOrThrow(_cursor, "dayOfWeek");
          final int _cursorIndexOfStartTime = CursorUtil.getColumnIndexOrThrow(_cursor, "startTime");
          final int _cursorIndexOfEndTime = CursorUtil.getColumnIndexOrThrow(_cursor, "endTime");
          final int _cursorIndexOfRoom = CursorUtil.getColumnIndexOrThrow(_cursor, "room");
          final int _cursorIndexOfNotes = CursorUtil.getColumnIndexOrThrow(_cursor, "notes");
          final List<ClassEntity> _result = new ArrayList<ClassEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final ClassEntity _item;
            final int _tmpId;
            _tmpId = _cursor.getInt(_cursorIndexOfId);
            final String _tmpTitle;
            if (_cursor.isNull(_cursorIndexOfTitle)) {
              _tmpTitle = null;
            } else {
              _tmpTitle = _cursor.getString(_cursorIndexOfTitle);
            }
            final String _tmpDayOfWeek;
            if (_cursor.isNull(_cursorIndexOfDayOfWeek)) {
              _tmpDayOfWeek = null;
            } else {
              _tmpDayOfWeek = _cursor.getString(_cursorIndexOfDayOfWeek);
            }
            final String _tmpStartTime;
            if (_cursor.isNull(_cursorIndexOfStartTime)) {
              _tmpStartTime = null;
            } else {
              _tmpStartTime = _cursor.getString(_cursorIndexOfStartTime);
            }
            final String _tmpEndTime;
            if (_cursor.isNull(_cursorIndexOfEndTime)) {
              _tmpEndTime = null;
            } else {
              _tmpEndTime = _cursor.getString(_cursorIndexOfEndTime);
            }
            final String _tmpRoom;
            if (_cursor.isNull(_cursorIndexOfRoom)) {
              _tmpRoom = null;
            } else {
              _tmpRoom = _cursor.getString(_cursorIndexOfRoom);
            }
            final String _tmpNotes;
            if (_cursor.isNull(_cursorIndexOfNotes)) {
              _tmpNotes = null;
            } else {
              _tmpNotes = _cursor.getString(_cursorIndexOfNotes);
            }
            _item = new ClassEntity(_tmpId,_tmpTitle,_tmpDayOfWeek,_tmpStartTime,_tmpEndTime,_tmpRoom,_tmpNotes);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @NonNull
  public static List<Class<?>> getRequiredConverters() {
    return Collections.emptyList();
  }
}
