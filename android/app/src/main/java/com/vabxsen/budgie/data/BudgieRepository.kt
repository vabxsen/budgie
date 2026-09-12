package com.vabxsen.budgie.data

import android.content.Context
import android.util.AtomicFile
import com.vabxsen.budgie.domain.BudgieCollection
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class BudgieRepository(context: Context) {
    private val file = AtomicFile(File(context.filesDir, "collection.json"))
    private val mutex = Mutex()
    private val mutable = MutableStateFlow<Result<BudgieCollection>?>(null)
    val state = mutable.asStateFlow()

    suspend fun load() =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                mutable.value = runCatching {
                    if (file.baseFile.exists() || File(file.baseFile.path + ".bak").exists())
                        CollectionCodec.decode(
                            file.openRead().bufferedReader().use { it.readText() }
                        )
                    else BudgieCollection()
                }
            }
        }

    suspend fun update(transform: (BudgieCollection) -> BudgieCollection) =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val current =
                    mutable.value?.getOrThrow() ?: error("Your collection is still loading.")
                val next = transform(current)
                val bytes = CollectionCodec.encode(next).toByteArray(Charsets.UTF_8)
                var stream: java.io.FileOutputStream? = null
                try {
                    stream = file.startWrite()
                    stream.write(bytes)
                    file.finishWrite(stream)
                } catch (e: Exception) {
                    file.failWrite(stream)
                    throw e
                }
                mutable.value = Result.success(next)
            }
        }
}
