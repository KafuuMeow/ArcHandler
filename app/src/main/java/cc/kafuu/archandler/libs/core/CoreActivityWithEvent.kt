package cc.kafuu.archandler.libs.core

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

abstract class CoreActivityWithEvent : CoreActivity() {
    private var _uiEffectCollectJob: Job? = null

    protected abstract fun getViewEventFlow(): Flow<ViewEventWrapper>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        registerViewEventFlow()
    }

    /**
     * 注册并收集一个[IViewEvent]流
     *
     * 此方法会在 Activity 的生命周期范围内自动管理协程收集任务：
     * - 如果之前已经存在一个收集任务，会先取消旧的任务，再启动新的收集；
     * - 只会在Activity的生命周期处于[Lifecycle.State.CREATED]后才会开始收集
     * - 注册后会处理默认的[IViewEvent]，但如果你需要处理其它Effect，则请重写[onReceivedViewEvent]
     *
     * @see onReceivedViewEvent
     */
    private fun registerViewEventFlow() {
        if (_uiEffectCollectJob?.isActive == true) _uiEffectCollectJob?.cancel()
        _uiEffectCollectJob = lifecycleScope.launch {
            getViewEventFlow()
                .flowWithLifecycle(lifecycle, Lifecycle.State.CREATED)
                .collect { wrapper ->
                    coroutineContext.ensureActive()
                    wrapper.consumeIfNotHandled {
                        onReceivedViewEvent(it)
                    }
                }
        }
    }

    /**
     * 处理由 [registerViewEventFlow] 收集到的 [IViewEvent] 事件
     *
     * 子类可以通过重写本方法，自定义处理逻辑，或在调用 `super.onReceivedViewEvent()` 前后增加额外行为
     *
     * @param viewEvent 收到的 UI 事件，具体类型由 [IViewEvent] 定义
     */
    protected open suspend fun onReceivedViewEvent(viewEvent: IViewEvent) {
        if (viewEvent is AppViewEvent) handleAppViewEvent(viewEvent)
    }

    /**
     * 处理AppViewEvent
     */
    private fun handleAppViewEvent(viewEvent: AppViewEvent) {
        when (viewEvent) {
            is AppViewEvent.PopupToastMessage -> {
                Toast.makeText(this, viewEvent.message, Toast.LENGTH_SHORT).show()
            }

            is AppViewEvent.PopupToastMessageByResId -> {
                Toast.makeText(this, getString(viewEvent.message), Toast.LENGTH_SHORT).show()
            }

            is AppViewEvent.StartActivity -> {
                val intent = Intent(this, viewEvent.activity).apply {
                    viewEvent.extras?.run { putExtras(this) }
                }
                startActivity(intent)
            }

            is AppViewEvent.StartActivityByIntent -> {
                startActivity(viewEvent.intent)
            }

            is AppViewEvent.SetResult -> {
                if (viewEvent.intent == null) {
                    setResult(viewEvent.resultCode)
                } else {
                    setResult(viewEvent.resultCode, viewEvent.intent)
                }
            }
        }
    }
}
