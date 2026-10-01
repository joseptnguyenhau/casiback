package com.casi.cashback.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.casi.cashback.data.AppDatabase
import com.casi.cashback.data.entity.TransactionEntity
import com.casi.cashback.data.entity.UserEntity
import com.casi.cashback.data.entity.WithdrawalEntity
import com.casi.cashback.data.repository.CashbackRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CashbackViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: CashbackRepository

    init {
        val database = AppDatabase.getDatabase(application)
        repository = CashbackRepository(database.appDao())
    }

    val userState: StateFlow<UserEntity?> = repository.getUserFlow()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    val transactionsState: StateFlow<List<TransactionEntity>> = repository.getTransactionsRealtimeFlow()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val withdrawalsState: StateFlow<List<WithdrawalEntity>> = repository.getWithdrawalsFlow()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // UI States for Home Screen Link Generation
    private val _originalLinkInput = MutableStateFlow("")
    val originalLinkInput: StateFlow<String> = _originalLinkInput.asStateFlow()

    private val _isConverting = MutableStateFlow(false)
    val isConverting: StateFlow<Boolean> = _isConverting.asStateFlow()

    private val _conversionResult = MutableStateFlow<Pair<String, Long>?>(null)
    val conversionResult: StateFlow<Pair<String, Long>?> = _conversionResult.asStateFlow()

    private val _uiMessage = MutableStateFlow<String?>(null)
    val uiMessage: StateFlow<String?> = _uiMessage.asStateFlow()

    fun updateOriginalLink(link: String) {
        _originalLinkInput.value = link
    }

    fun convertLink() {
        val link = _originalLinkInput.value.trim()
        if (link.isBlank()) {
            _uiMessage.value = "Vui lòng nhập hoặc dán link Shopee sản phẩm!"
            return
        }

        viewModelScope.launch {
            _isConverting.value = true
            _uiMessage.value = null
            // Gọi Cloud Function createAffiliateLink qua Repository (An toàn, không hardcode token trên client)
            val result = repository.convertAndSaveLink(link)
            _isConverting.value = false
            result.fold(
                onSuccess = { pair ->
                    _conversionResult.value = pair
                    _uiMessage.value = "Đã tạo link hoàn tiền thành công!"
                },
                onFailure = { error ->
                    _uiMessage.value = error.message ?: "Có lỗi xảy ra khi tạo link hoàn tiền."
                }
            )
        }
    }

    fun clearConversionResult() {
        _conversionResult.value = null
        _originalLinkInput.value = ""
    }

    fun requestWithdrawal(
        bankName: String,
        accountNumber: String,
        accountHolder: String,
        amount: Long,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            val result = repository.requestWithdrawal(bankName, accountNumber, accountHolder, amount)
            result.fold(
                onSuccess = { msg ->
                    _uiMessage.value = msg
                    onSuccess()
                },
                onFailure = { err ->
                    _uiMessage.value = err.message ?: "Lỗi yêu cầu rút tiền."
                }
            )
        }
    }

    val userProfileState: StateFlow<Map<String, Any>?> = repository.getUserProfileFlow()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    val withdrawalRequestsState: StateFlow<List<Map<String, Any>>> = repository.getWithdrawalRequestsFlow()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun updateProfile(data: Map<String, Any>, onSuccess: () -> Unit) {
        viewModelScope.launch {
            val result = repository.updateUserProfile(data)
            result.fold(
                onSuccess = {
                    _uiMessage.value = "Cập nhật thông tin thành công!"
                    onSuccess()
                },
                onFailure = { err ->
                    _uiMessage.value = err.message ?: "Lỗi cập nhật thông tin."
                }
            )
        }
    }

    fun submitWithdrawalRequest(
        amount: Long,
        bankName: String,
        accountNumber: String,
        accountHolder: String,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            val result = repository.createWithdrawalRequest(amount, bankName, accountNumber, accountHolder)
            result.fold(
                onSuccess = { msg ->
                    _uiMessage.value = msg
                    onSuccess()
                },
                onFailure = { err ->
                    _uiMessage.value = err.message ?: "Lỗi yêu cầu rút tiền."
                }
            )
        }
    }

    fun clearMessage() {
        _uiMessage.value = null
    }
}
