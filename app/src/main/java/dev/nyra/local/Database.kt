package dev.nyra.local

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow
import java.util.UUID

@Entity data class Chat(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val title: String = "Nova conversa", val updated: Long = System.currentTimeMillis(),
    val pinned: Boolean = false, val archived: Boolean = false
)
@Entity(foreignKeys = [ForeignKey(entity = Chat::class, parentColumns = ["id"], childColumns = ["chatId"], onDelete = ForeignKey.CASCADE)], indices = [Index("chatId")])
data class ChatMessage(
    @PrimaryKey val id: String = UUID.randomUUID().toString(), val chatId: String,
    val role: String, val text: String, val created: Long = System.currentTimeMillis(),
    val state: String = "complete"
)
@Entity data class Memory(
    @PrimaryKey val id: String = UUID.randomUUID().toString(), val text: String,
    val sourceChatId: String? = null, val pinned: Boolean = false,
    val kind: String = "semantic", val updated: Long = System.currentTimeMillis()
)
@Dao interface NyraDao {
    @Query("SELECT * FROM Chat ORDER BY pinned DESC, updated DESC") fun chats(): Flow<List<Chat>>
    @Query("SELECT * FROM ChatMessage WHERE chatId=:id ORDER BY created, rowid") fun messages(id: String): Flow<List<ChatMessage>>
    @Query("SELECT * FROM ChatMessage WHERE chatId=:id ORDER BY created, rowid") suspend fun history(id: String): List<ChatMessage>
    @Query("SELECT * FROM Memory ORDER BY pinned DESC, updated DESC") fun memories(): Flow<List<Memory>>
    @Query("SELECT * FROM Memory ORDER BY pinned DESC, updated DESC") suspend fun memorySnapshot(): List<Memory>
    @Query("UPDATE Chat SET updated=:time WHERE id=:id") suspend fun touchChat(id: String, time: Long)
    @Upsert suspend fun put(chat: Chat)
    @Upsert suspend fun put(message: ChatMessage)
    @Upsert suspend fun put(memory: Memory)
    @Query("DELETE FROM Chat WHERE id=:id") suspend fun deleteChat(id: String)
    @Query("DELETE FROM Memory WHERE id=:id") suspend fun deleteMemory(id: String)
    @Query("UPDATE ChatMessage SET state='interrupted' WHERE state='generating'") suspend fun recover()
}
@Database(entities = [Chat::class, ChatMessage::class, Memory::class], version = 1, exportSchema = true)
abstract class NyraDatabase : RoomDatabase() {
    abstract fun dao(): NyraDao
    companion object {
        fun open(context: Context) = Room.databaseBuilder(context, NyraDatabase::class.java, "nyra.db")
            .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING).build()
    }
}
