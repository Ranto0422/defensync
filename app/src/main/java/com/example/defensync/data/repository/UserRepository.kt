package com.example.defensync.data.repository

import com.example.defensync.data.dto.toDomain
import com.example.defensync.data.dto.toRanges
import com.example.defensync.data.remote.UserApi
import com.example.defensync.domain.model.TimeRange
import com.example.defensync.domain.model.UserProfile
import com.example.defensync.domain.model.Weekday

class UserRepository(private val userApi: UserApi) {

    suspend fun professors(): List<UserProfile> = userApi.professors().map { it.toDomain() }

    suspend fun students(): List<UserProfile> = userApi.students().map { it.toDomain() }

    suspend fun getDefaultAvailability(): Map<Weekday, List<TimeRange>> {
        val wire = userApi.getAvailability().availability
        return Weekday.entries
            .associateWith { day -> wire[day.name].orEmpty().toRanges() }
            .filterValues { it.isNotEmpty() }
    }

    suspend fun saveDefaultAvailability(availability: Map<Weekday, List<TimeRange>>) {
        userApi.updateAvailability(availability.toWire())
    }
}
