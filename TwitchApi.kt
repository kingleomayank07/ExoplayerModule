package com.naseeb.exoplayer

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.POST

interface TwitchApi {

    @Headers("Content-Type: application/json")
    @POST("gql/")
    suspend fun getToken(
        @Body body: String,
        @Header("Client-ID") Client: String,
    ): Response<String>

    /*@GET("{karlaplan}/access_token")
    suspend fun getToken(
        @Header("Client-ID") Client: String,
        @Path("karlaplan") ChannelName: String
    ): ResponseBody*/

}

//http://usher.twitch.tv/api/channel/hls/
// dreamhackcs.m3u8?player=twitchweb&
// &token={"adblock":false,"authorization":{"forbidden":false,"reason":""},"blackout_enabled":false,"channel":"dreamhackcs","channel_id":22859264,"chansub":{"restricted_bitrates":[],"view_until":1924905600},"ci_gb":false,"geoblock_reason":"","device_id":"IWhNt2UG3TBJFtjnbfNngcN18RxFOhdM","expires":1607010612,"extended_history_allowed":false,"game":"","hide_ads":false,"https_required":false,"mature":false,"partner":false,"platform":null,"player_type":null,"private":{"allowed_to_view":true},"privileged":false,"role":"","server_ads":true,"show_ads":true,"subscriber":false,"turbo":false,"user_id":null,"user_ip":"110.235.230.205","version":2}&
// sig=a415f7102b7d9bceda56e77910dfb767f58483e3&
// allow_audio_only=true&
// allow_source=true&
// type=any&
// p=39114