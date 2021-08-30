package com.naseeb.exoplayer

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.analytics.AnalyticsListener
import com.google.android.exoplayer2.ext.okhttp.OkHttpDataSourceFactory
import com.google.android.exoplayer2.metadata.Metadata
import com.google.android.exoplayer2.source.BehindLiveWindowException
import com.google.android.exoplayer2.source.MediaSource
import com.google.android.exoplayer2.source.MediaSourceEventListener
import com.google.android.exoplayer2.source.ProgressiveMediaSource
import com.google.android.exoplayer2.source.dash.DashMediaSource
import com.google.android.exoplayer2.source.dash.DefaultDashChunkSource
import com.google.android.exoplayer2.source.hls.HlsMediaSource
import com.google.android.exoplayer2.source.smoothstreaming.DefaultSsChunkSource
import com.google.android.exoplayer2.source.smoothstreaming.SsMediaSource
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector
import com.google.android.exoplayer2.ui.DefaultTrackNameProvider
import com.google.android.exoplayer2.ui.PlayerView
import com.google.android.exoplayer2.upstream.DataSource
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory
import com.google.android.exoplayer2.upstream.cache.SimpleCache
import com.google.android.exoplayer2.util.Util
import com.naseeb.log.LogUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import org.json.JSONObject
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.*


/*object*/class PlayerImpl : IPlayer, Player.EventListener, AnalyticsListener,
    MediaSourceEventListener {

    //region variables
    private val TAG = PlayerImpl::class.java.canonicalName
    private var mPlayer: SimpleExoPlayer? = null
    private var mToken: Int? = null
    private var mMediaSource: MediaSource? = null
    private var mSimpleCache: SimpleCache? = null
    private var mTimeLine = MutableLiveData<Int>()
    private var mUri: String? = null
    private var mGetCurrentTimeDifference = 0
    private var mIsStartedPlayingFirstTime: Boolean = true
    private lateinit var mContext: Context
    private lateinit var mChannelName: String
    private lateinit var mPlayerView: PlayerView
    private lateinit var mPlayerCallback: IPlayer.PlayerCallback
    private var retryView: ImageView? = null
    private var prevousStreamWidth: Int = 0
    private var prevousStreamHeight: Int = 0
    //endregion

    override fun pause() {
        LogUtil.debugLog(TAG, "pause")
        if (mPlayer?.isPlaying!!) {
            mPlayer!!.playWhenReady = false
            mPlayer!!.pause()
        } else {
            mPlayer!!.play()
            mPlayer!!.playWhenReady = true
        }
    }

    override fun init(
        context: Context,
        channelName: String,
        playerView: PlayerView,
        playerCallback: IPlayer.PlayerCallback
    ) {
        LogUtil.debugLog(TAG, "init")
        this.mContext = context
        this.mChannelName = channelName
        this.mPlayerView = playerView
        this.mPlayerCallback = playerCallback
        start()
        if (retryView != null) {
            mPlayerView.removeView(retryView)
        }
        setRefreshButtonUi(mContext)
    }

    private fun setRefreshButtonUi(context: Context) {
        retryView = null
        retryView = ImageView(context)
        retryView?.setBackgroundColor(
            ContextCompat.getColor(
                context,
                android.R.color.black
            )
        )
        retryView?.setImageResource(R.drawable.ic_action_refresh)
        val retryHeightWidth = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            50.0F,
            retryView?.context?.resources?.displayMetrics
        ).toInt()

        retryView?.layoutParams =
            FrameLayout.LayoutParams(retryHeightWidth, retryHeightWidth, Gravity.CENTER)
        mPlayerView.addView(retryView)
        hideRetryView()
        retryView?.setOnClickListener {
            mUri = null
            start()
        }
    }

    private fun stopPlayer(isReset: Boolean) {
        LogUtil.debugLog(TAG, "stopPlayer isReset : $isReset mExoPlayer : $mPlayer")
        if (mPlayer != null) {
            if (mMediaSource != null) {
                mMediaSource!!.removeEventListener(this)
            }
            mMediaSource = null
            if (isReset) { //If player is getting reset
                //Removing analytics listener
                mPlayer!!.removeAnalyticsListener(this)
                //Removing player event listener
                mPlayer!!.removeListener(this)
            }
            mPlayer!!.stop()
            //We have done it to switch player view. Otherwise, we will not be able
            //to set same player to new player view and player view will become blank.
            //Best way to do this is PlayerView.switchTargetView()
            mPlayerView.player = null
            if (isReset) {
                mSimpleCache?.release()
                ExoPlayerInstanceManager.Instance.getInstance(mContext).release(mToken)
                mPlayer = null
                mToken = null
            }
        }
    }

    override fun resume() {
        LogUtil.debugLog(TAG, "resume")
        mPlayer!!.playWhenReady = true
    }

    override fun seekTo(position: Long) {
        mPlayer?.seekTo(position)
    }

    override fun release() {
        LogUtil.debugLog(TAG, "release")
        stopPlayer(true)
    }

    override fun getStreamTime(): LiveData<Int?> {
        return mTimeLine
    }

    override fun getTimeDifference(): Int {
        return mGetCurrentTimeDifference
    }

    override fun play() {
//        LogUtil.debugLog(TAG, "play uri : $uri")
        //Initializing exoplayer
        if (mPlayer == null) {
            LogUtil.debugLog(TAG, "play initializing player as it is null")
            val simpleExoPlayerWrapper =
                ExoPlayerInstanceManager.Instance.getInstance(mContext).freeInstanceOfExoPlayer
            mPlayer = simpleExoPlayerWrapper?.simpleExoPlayer
            mToken = simpleExoPlayerWrapper?.token
            mPlayer?.addListener(this)
            mPlayer?.addAnalyticsListener(this)
            //mPlayerView.setKeepContentOnPlayerReset(true)
        } else {
            LogUtil.debugLog(TAG, "play not initializing player as it is already initialized")
        }
        mPlayerView.player = mPlayer
        //Playing content in exo player
        if (!mUri.isNullOrEmpty()) {
            mMediaSource = buildMediaSource(Uri.parse(mUri))
            mMediaSource!!.addEventListener(Handler(Looper.myLooper()!!), this)
            /*val simpleExoPlayerWrapper =
                ExoPlayerInstanceManager.Instance.getInstance(mContext).freeInstanceOfExoPlayer
            mPlayer = simpleExoPlayerWrapper?.simpleExoPlayer
            mToken = simpleExoPlayerWrapper?.token
            mPlayer?.addListener(this)
            mPlayer?.addAnalyticsListener(this)
            mPlayerView.setKeepContentOnPlayerReset(true)
            mPlayerView.player = mPlayer*/
            mPlayer!!.setMediaSource(mMediaSource!!)
            mPlayer!!.prepare()
            mPlayer!!.playWhenReady = true
            mIsStartedPlayingFirstTime = true
        } else {
            Toast.makeText(mContext, "Unexpected error occurred!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun buildMediaSource(uri: Uri): MediaSource {
        LogUtil.debugLog(TAG, "buildMediaSource uri : $uri")
        return when (@C.ContentType val type = Util.inferContentType(uri)) {
            C.TYPE_DASH -> {
                LogUtil.debugLog(TAG, "buildMediaSource TYPE_DASH")
                DashMediaSource.Factory(
                    DefaultDashChunkSource.Factory(buildDataSourceFactory()),
                    buildDataSourceFactory()
                ).createMediaSource(uri)
            }
            C.TYPE_SS -> {
                LogUtil.debugLog(TAG, "buildMediaSource TYPE_SS")
                SsMediaSource.Factory(
                    DefaultSsChunkSource.Factory(buildDataSourceFactory()),
                    buildDataSourceFactory()
                ).createMediaSource(uri)
            }
            C.TYPE_HLS -> {
                LogUtil.debugLog(TAG, "buildMediaSource TYPE_HLS")

                //creating okHttpDataSourceFactory with network interceptor
                val okHttpDataSourceFactory = OkHttpDataSourceFactory(
                    OkHttpClient().newBuilder()
                        //adding Interceptor
                        .addInterceptor(PlayerInterceptor())
                        .build(),
                    "app_name",
                    DefaultBandwidthMeter.Builder(mContext).build()
                )


                //creating HlsMediaSource with okHttpDataSourceFactory
                HlsMediaSource.Factory(okHttpDataSourceFactory)
                    .setAllowChunklessPreparation(true)
                    .createMediaSource(MediaItem.Builder().setUri(uri).build())
            }
            C.TYPE_OTHER -> {
                LogUtil.debugLog(TAG, "buildMediaSource TYPE_OTHER")
                ProgressiveMediaSource.Factory(buildDataSourceFactory()).createMediaSource(uri)
            }
            else -> {
                LogUtil.debugLog(TAG, "buildMediaSource default")
                throw IllegalStateException("Unsupported type: $type")
            }
        }
    }

    private fun buildDataSourceFactory(): DataSource.Factory {
        LogUtil.debugLog(TAG, "buildDataSourceFactory")
        // Specify cache folder, my cache folder named media which is inside getCacheDir.
        /*val cacheFolder = File(mContext.cacheDir, "exoplayer_cache")
        LogUtil.debugLog(TAG, "buildDataSourceFactory cacheFolder : $cacheFolder")
        val cacheSize = 200L
        LogUtil.debugLog(TAG, "buildDataSourceFactory cache size : $cacheSize MB")
        // Specify cache size and removing policies*/
        // My cache size will be cacheSize * 1MB and it will automatically remove least recently used files if the size is
        // reached out.
//        val cacheEvictor = LeastRecentlyUsedCacheEvictor(cacheSize * 1024 * 1024)
        //Database provider
//        val databaseProvider: DatabaseProvider = ExoDatabaseProvider(mContext)
        // Build cache
        /* mSimpleCache = SimpleCache(cacheFolder, cacheEvictor, databaseProvider)
         // Build data source factory with cache enabled, if data is available in cache it will return immediately,
         // otherwise it will open a new connection to get the data.

         *//*return CacheDataSourceFactory(

            mSimpleCache!!, DefaultDataSourceFactory(
                mContext, DefaultHttpDataSourceFactory
                    ("app_name")
            )
        )*/
        return DefaultDataSourceFactory(mContext)
    }

    @Override
    override fun onPlayerStateChanged(playWhenReady: Boolean, playbackState: Int) {

        LogUtil.debugLog(
            TAG, "PlayerEventListener onPlayerStateChanged playWhenReady : " +
                    playWhenReady + " playbackState : " + playbackState + " "
        )
        when (playbackState) {
            Player.STATE_READY -> {
                mPlayerCallback.onBufferingEnded()
                Log.d(TAG, "onBufferingStarted: ${mPlayer?.totalBufferedDuration}")
                LogUtil.debugLog(
                    TAG, "PlayerEventListener onPlayerStateChanged Player.STATE_READY " +
                            "mIsStartedPlayingFirstTime : $mIsStartedPlayingFirstTime"
                )
                //We have to update video duration only first time when a video starts playing
                if (mIsStartedPlayingFirstTime) {
                    mIsStartedPlayingFirstTime = false
                    if (mPlayer != null) {
                        mPlayerCallback.onMediaDurationFetched(mPlayer!!.duration)
                    } else {
                        LogUtil.debugLog(
                            TAG, "PlayerEventListener onPlayerStateChanged Player.STATE_READY " +
                                    "mPlayerCallback onVideoDurationFetched player instance is null"
                        )
                    }
                } else {
                    mPlayerCallback.onBufferingEnded()
                }
            }
            Player.STATE_ENDED -> {
                mPlayerCallback.onPlayEnded()
            }
            Player.STATE_BUFFERING -> {
                mPlayerCallback.onBufferingStarted()
            }
            Player.STATE_IDLE -> {

            }
        }
    }

    override fun onPlayerError(error: ExoPlaybackException) {
        LogUtil.errorLog(TAG, "onPlayerError() error: ${error.message}")
        when (error.type) {
            ExoPlaybackException.TYPE_SOURCE -> {
                LogUtil.errorLog(TAG, "TYPE_SOURCE: " + error.sourceException.message)
                if (error.sourceException is BehindLiveWindowException) {
                    mPlayerCallback.onPlayerNetworkError()
                } else {
                    showRetryView()
                }
            }
            /* ExoPlaybackException.TYPE_UNEXPECTED -> {
                 LogUtil.errorLog(TAG, "TYPE_UNEXPECTED: " + error.unexpectedException.message)
             }
             ExoPlaybackException.TYPE_OUT_OF_MEMORY -> {
                 LogUtil.errorLog(TAG, "TYPE_OUT_OF_MEMORY: " + error.unexpectedException.message)
             }
             ExoPlaybackException.TYPE_REMOTE -> {
                 LogUtil.errorLog(TAG, "TYPE_REMOTE: " + error.unexpectedException.message)
             }*/
            else -> {
                LogUtil.debugLog(TAG, "Stream error else case")
                showRetryView()
            }
        }
    }

    @SuppressLint("SimpleDateFormat")
    override fun onMetadata(eventTime: AnalyticsListener.EventTime, metadata: Metadata) {
        if (prevousStreamWidth != mPlayer?.videoFormat?.width || prevousStreamHeight != mPlayer?.videoFormat?.height) {
            prevousStreamWidth = mPlayer?.videoFormat?.width!!
            prevousStreamHeight = mPlayer?.videoFormat?.height!!
            LogUtil.debugLog(
                TAG,
                "onResolution: stream width & height $prevousStreamWidth $prevousStreamHeight"
            )
        }
        for (i in 0 until metadata.length()) {
            if (metadata[i].toString().contains("${Calendar.getInstance().get(Calendar.YEAR)}-")) {
                val sdf: DateFormat = SimpleDateFormat("yyyy-MM-dd'T'hh:mm:ss")
                val formattedDate: Date = sdf.parse(metadata[i].toString().split("value=")[1])
                val cal = Calendar.getInstance()
                cal.time = sdf.parse(metadata[i].toString().split("value=")[1])
                cal.add(Calendar.HOUR, -1)
                val oneHourBack = cal.time
                sdf.timeZone = TimeZone.getTimeZone("IST")
                val c = Calendar.getInstance().time
                val sdf1: DateFormat = SimpleDateFormat("yyyy-MM-dd'T'hh:mm:ss")
                val mills: Long = c.time - sdf1.parse(sdf.format(oneHourBack))?.time!!
                val secs = (mills / (1000) % 60).toInt()
                mGetCurrentTimeDifference = secs
                mTimeLine.postValue(secs)
                if (secs > 30) {
                    goLive()
                }
                LogUtil.debugLog(TAG, "onMetadata final secs: $secs")
            }
        }
    }

    override fun stop() {
        LogUtil.debugLog(TAG, "stop")
        //mPlayer?.stop()
        //mPlayer?.release()
        stopPlayer(false)
    }

    override fun goLive() {
        LogUtil.debugLog(TAG, "goLive")
        mPlayer?.seekTo(C.TIME_UNSET)
        changeQuality("auto")
    }

    override fun isPaused(): Boolean {
        LogUtil.debugLog(TAG, "isPaused")
        return mPlayer != null && Player.STATE_READY == mPlayer!!.playbackState && !mPlayer!!.playWhenReady
    }

    override fun start() {
        LogUtil.debugLog(TAG, "start")
//        setUI()
        if (mUri.isNullOrEmpty()) {
            LogUtil.debugLog(TAG, "start mUri is null or empty. Fetching it again from twitch")
            CoroutineScope(Dispatchers.IO).launch {
                getAccessToken(mChannelName)
            }
        } else {
            LogUtil.debugLog(TAG, "start mUri is available. Using same uri")
            hideRetryView()
            play()
        }
    }

    private fun showRetryView() {
        LogUtil.debugLog(TAG, "showRetryView")
        retryView?.visibility = View.VISIBLE
    }

    private fun hideRetryView() {
        LogUtil.debugLog(TAG, "hideRetryView")
        retryView?.visibility = View.GONE
    }

    /*private fun setUI() {
        LogUtil.debugLog("setUI", "function called!")
        mPlayerView.findViewById<CardView>(R.id.live_background).setOnClickListener {

        }
        mPlayerView.findViewById<ImageButton>(R.id.exo_play).setOnClickListener {
            play()
        }
        mPlayerView.findViewById<ImageButton>(R.id.exo_settings).setOnClickListener {
            val popupMenu = PopupMenu(mContext, it)
            for (track in getTrackNames()) {
                popupMenu.menu.add(track)
            }
            popupMenu.show()
            customTrackSelection(popupMenu)
        }
     mPlayerView.findViewById<ImageButton>(R.id.exo_fullscreen).setOnClickListener {

     }
    }*/

    private fun customTrackSelection(popupMenu: PopupMenu) {
        popupMenu.setOnMenuItemClickListener {
            val maxWidthHeight = it.title
            changeQuality(maxWidthHeight)
            true
        }
    }

    /*private suspend fun getTwitchToken(channelName: String?) {
        LogUtil.debugLog(TAG, "getTwitchToken: $mChannelName")

        try {
            val response = RetrofitClient.instance.getToken(
                mContext.getString(R.string.client_id),
                channelName!!
            )
            val streams = JSONObject(response.string())
            CoroutineScope(Dispatchers.Main).launch {
                mUri = getTwitchStreams(streams, channelName)
                if (!mUri.isNullOrEmpty()) {
                    play()
                }
            }
        } catch (e: Exception) {
            LogUtil.debugLog(TAG, "getTwitchToken exception : ${e.localizedMessage}")
            count++
            if (count <= 5) {
                getTwitchToken(channelName)
            } else {
                LogUtil.debugLog(TAG, "getTwitchToken stream offline.")
            }
        }
    }*/

    private suspend fun getAccessToken(channelName: String) {
        LogUtil.debugLog(TAG, "getAccessToken channel name = $channelName")
        val retrofit = RetrofitClient.graphQLService
        val paramObject = JSONObject()
        paramObject.put(
            "query", """query {
              streamPlaybackAccessToken(
                channelName: "$channelName",
                params: {
                  platform: "web",
                  playerBackend: "mediaplayer",
                  playerType: "site"
                }
              )
              {
                value
                signature
              }
            }"""
        )

        CoroutineScope(Dispatchers.Main).launch {
            try {
                val response = retrofit.getToken(
                    paramObject.toString(),
                    mContext.getString(R.string.client_id)
                )
                val tokenObject = JSONObject(response.body().toString())
                LogUtil.debugLog(TAG, "getAccessToken: $tokenObject")
                mUri = getTwitchStreams(tokenObject, mChannelName)
                if (!mUri.isNullOrEmpty()) {
                    hideRetryView()
                    play()
                }
            } catch (e: Exception) {
                LogUtil.debugLog(TAG, "getAccessToken exception ${e.localizedMessage}")
                e.printStackTrace()
                showRetryView()
            }
        }
    }

    private fun getTwitchStreams(tokenObject: JSONObject?, channelName: String): String {
        LogUtil.debugLog(TAG, "getTwitchStreams: token = $tokenObject channel name = $channelName")
        var uri: String? = null
        if (tokenObject != null) {
            val random = (10000..99999).random()
            uri = "http://usher.twitch.tv/api/channel/hls/${
                channelName
            }.m3u8?player=twitchweb&token=${
                tokenObject.getJSONObject("data")
                    .getJSONObject("streamPlaybackAccessToken")
                    .getString("value")
            }&sig=${
                tokenObject.getJSONObject("data")
                    .getJSONObject("streamPlaybackAccessToken")
                    .getString("signature")
            }&allow_audio_only=false&allow_source=true&type=any&p=$random"
            LogUtil.debugLog(TAG, "getTwitchStreams: $uri")
        }
        return uri!!
    }

    private fun getTrackNames(): List<String> {
        LogUtil.debugLog(TAG, "getTrackNames()")
        val qualities = ArrayList<String>()
        qualities.clear()
        qualities.add("auto")
        val mappedTrackInfo =
            (mPlayer?.trackSelector as DefaultTrackSelector).currentMappedTrackInfo
        if (mappedTrackInfo != null) {
            val trackGroupArray = mappedTrackInfo.getTrackGroups(0)
            for (groupIndex in 0 until trackGroupArray.length) {
                for (trackIndex in 0 until trackGroupArray.get(groupIndex).length) {
                    val trackName = DefaultTrackNameProvider(mContext.resources).getTrackName(
                        trackGroupArray.get(groupIndex).getFormat(trackIndex)
                    )
                    qualities.add(trackName)
                }
            }
        } else {
            Toast.makeText(mContext, "Unexpected error occurred!", Toast.LENGTH_SHORT).show()
            return qualities
        }
        return qualities
    }

    private fun changeQuality(maxWidthHeight: CharSequence) {
        LogUtil.debugLog("PlayerImpl", "changeQuality: $maxWidthHeight")
        val trackSelector = (mPlayer?.trackSelector as DefaultTrackSelector)
        if (maxWidthHeight != "auto") {
            val getTitle = maxWidthHeight.split(",").toTypedArray()
            val getHeightWidth = getTitle[0].split("×").toTypedArray()
            val params = trackSelector.buildUponParameters()
                .setMaxVideoSize(
                    getHeightWidth[0].trim().toInt(),
                    getHeightWidth[1].trim().toInt()
                )
                .setAllowVideoNonSeamlessAdaptiveness(true)
            trackSelector.setParameters(params)
            //play()
        } else {
            val builder = trackSelector.buildUponParameters().clearVideoSizeConstraints()
            trackSelector.setParameters(builder)
        }
    }

}
