package com.developer_rahul.docunova.Fragments

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class SharedViewModel : ViewModel() {
    private val _scanTrigger = MutableLiveData<Boolean>()
    val scanTrigger: LiveData<Boolean> get() = _scanTrigger

    fun triggerScan() {
        _scanTrigger.value = true
    }

    fun resetTrigger() {
        _scanTrigger.value = false
    }
}
