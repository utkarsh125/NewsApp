package com.example.newsapp.core.domain

sealed class NewsResult<T>(
    val data: T? = null,
    val error:  String?
){
    //if we successfully get the data then return it
    class Success<T>(data: T?):NewsResult<T>(data, null)

    //if we don't get any data, send the error
    class Error<T>(error:String?): NewsResult<T>(null, error)
}