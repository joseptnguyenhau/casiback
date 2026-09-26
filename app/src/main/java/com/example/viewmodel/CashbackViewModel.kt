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

    private val _conversionResult = MutableStateFlow<Pair<String, Double>?>(null)
    val conversionResult: StateFlow<Pair<String, Double>?> = _conversionResult.asStateFlow()

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
            // Gọi hàm repository lưu giao dịch và cập nhật Firestore
            val result = repository.saveTransactionToFirestore(repository.currentUserId, link)
            _isConverting.value = false
            result.fold(
                onSuccess = { pair ->
                    _conversionResult.value = pair
                    _uiMessage.value = "Kích hoạt hoàn tiền thành công!"
                },
                onFailure = { error ->
                    _uiMessage.value = error.message ?: "Có lỗi xảy ra khi kích hoạt hoàn tiền."
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
        amount: Double,
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

    fun registerUser(
        name: String,
        email: String,
        phone: String,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            val result = repository.registerUser(name, email, phone)
            result.fold(
                onSuccess = { msg ->
                    _uiMessage.value = msg
                    onSuccess()
                },
                onFailure = { err ->
                    _uiMessage.value = err.message ?: "Đăng ký thất bại."
                }
            )
        }
    }

    fun clearMessage() {
        _uiMessage.value = null
    }
}
