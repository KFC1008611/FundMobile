package com.example.fundmobile.data.model

data class FundGroup(
    val id: String,
    val name: String,
    val codes: MutableList<String> = mutableListOf()
)
