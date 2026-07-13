package com.endgamefinance.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.endgamefinance.data.db.entity.Category
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {

    @Query("SELECT * FROM categories ORDER BY type, name")
    fun observeAll(): Flow<List<Category>>

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun getById(id: String): Category?

    @Query("SELECT * FROM categories")
    suspend fun getAllOnce(): List<Category>

    @Insert
    suspend fun insert(category: Category)

    @Update
    suspend fun update(category: Category)

    /**
     * Fails with a constraint exception if the category is referenced by
     * splits/budgets/reminders — callers surface that as "in use" rather than
     * silently orphaning history. Children are detached (parent_id → NULL) per schema.
     */
    @Delete
    suspend fun delete(category: Category)
}
