package com.endgamefinance.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "tags",
    indices = [Index(value = ["name"], unique = true)],
)
data class Tag(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "name") val name: String,
)

@Entity(
    tableName = "transaction_tags",
    primaryKeys = ["transaction_id", "tag_id"],
    foreignKeys = [
        ForeignKey(
            entity = TransactionEntity::class,
            parentColumns = ["id"],
            childColumns = ["transaction_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = Tag::class,
            parentColumns = ["id"],
            childColumns = ["tag_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("tag_id")],
)
data class TransactionTag(
    @ColumnInfo(name = "transaction_id") val transactionId: String,
    @ColumnInfo(name = "tag_id") val tagId: String,
)
