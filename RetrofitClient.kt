package com.naseeb.exoplayer

import retrofit2.Retrofit
import retrofit2.converter.scalars.ScalarsConverterFactory

object RetrofitClient {

    /*private const val BASE_URL = "http://api.twitch.tv/api/channels/"

    val instance: TwitchApi by lazy {
        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        retrofit.create(TwitchApi::class.java)
    }*/
    private const val BASE_URL: String = "https://gql.twitch.tv/"

    val graphQLService: TwitchApi by lazy {
        Retrofit
            .Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(ScalarsConverterFactory.create())
            .build().create(TwitchApi::class.java)
    }
}