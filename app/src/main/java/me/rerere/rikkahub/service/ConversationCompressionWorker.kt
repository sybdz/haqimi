package me.rerere.rikkahub.service

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlin.uuid.Uuid

private const val TAG = "ConversationCompressionWorker"

class ConversationCompressionWorker(
    appContext: Context,
    workerParams: WorkerParameters,
    private val chatService: ChatService,
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        val conversationId = inputData.getString(INPUT_CONVERSATION_ID)
            ?.let { rawId -> runCatching { Uuid.parse(rawId) }.getOrNull() }
            ?: return Result.failure()

        return runCatching {
            chatService.executeAutoCompressionWork(conversationId)
            Result.success()
        }.getOrElse { error ->
            if (error is AutoCompressionDeferredException) {
                Log.d(TAG, "Auto compression deferred for $conversationId")
                return Result.retry()
            }
            Log.w(TAG, "Auto compression failed for $conversationId", error)
            Result.success()
        }
    }
}
