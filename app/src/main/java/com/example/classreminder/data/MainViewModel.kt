package com.example.classreminder.data

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = AppDatabase.getInstance(application).classDao()

    private val _classes = MutableStateFlow<List<ClassEntity>>(emptyList())
    val classes: StateFlow<List<ClassEntity>> = _classes.asStateFlow()

    init {
        loadClasses()
    }

    private fun loadClasses() {
        viewModelScope.launch {
            _classes.value = dao.getAll()
        }
    }

    /** Insert a new class or replace an existing one (by id). */
    fun save(entity: ClassEntity) {
        viewModelScope.launch {
            dao.insert(entity)
            loadClasses()
        }
    }

    /** Delete a class. */
    fun delete(entity: ClassEntity) {
        viewModelScope.launch {
            dao.delete(entity)
            loadClasses()
        }
    }
}
