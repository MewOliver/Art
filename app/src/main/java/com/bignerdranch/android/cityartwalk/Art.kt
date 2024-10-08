package com.bignerdranch.android.cityartwalk

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date
import java.util.UUID

@Entity
data class Art(
    @PrimaryKey val id: UUID,
    val title: String,
    val date: Date,
    val address: String?,
    val longitude: Double?,
    val latitude: Double?,
    val photoFileName: String? = null
)
