package com.naseeb.exoplayer

import android.content.Context
import androidx.lifecycle.LiveData
import com.google.android.exoplayer2.ui.PlayerView

//TODO need to move getInstance() method to some other class. Here only player related method will be there
interface IPlayer {

    interface PlayerCallback {
        fun onBufferingEnded()
        fun onBufferingStarted()
        fun onPlayEnded()
        fun onMediaDurationFetched(videoDuration: Long)
        fun onPlayerNetworkError() { /* default implementation */ }
    }

    companion object {
        fun getInstance(): IPlayer = PlayerImpl()
    }

    fun play()
    fun pause()
    fun stop()
    fun start()
    fun resume()
    fun seekTo(position: Long)
    fun release()
    fun getStreamTime(): LiveData<Int?>
    fun getTimeDifference(): Int
    fun goLive()
    fun isPaused():Boolean

    fun init(
        context: Context,
        channelName: String,
        playerView: PlayerView,
        playerCallback: PlayerCallback
    )
}