package com.endgamefinance.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.endgamefinance.data.db.entity.CategoryGroup
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryGroupDao {

    @Query("SELECT * FROM category_groups ORDER BY type, name")
    fun observeAll(): Flow<List<CategoryGroup>>

    @Query("SELECT * FROM category_groups")
    suspend fun getAllOnce(): List<CategoryGroup>

    @Query("SELECT COUNT(*) FROM categories WHERE group_id = :groupId")
    suspend fun categoryCount(groupId: String): Long

    @Insert
    suspend fun insert(group: CategoryGroup)

    @Update
    suspend fun update(group: CategoryGroup)

    /** Callers must ensure the group is empty first (see [categoryCount]). */
    @Delete
    suspend fun delete(group: CategoryGroup)
}
