package com.example.taskmanager.domain.model

import com.google.gson.annotations.SerializedName

data class NativeProcessInfo(
    @SerializedName("pid") val pid: Int,
    @SerializedName("name") val name: String,
    @SerializedName("ram_usage") val ramUsage: Long
)
