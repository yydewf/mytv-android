package top.yogiczy.mytv.tv.ui.screensold.main.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.yogiczy.mytv.core.data.entities.channel.Channel
import top.yogiczy.mytv.core.data.entities.channel.ChannelGroupList
import top.yogiczy.mytv.core.data.entities.channel.ChannelGroupList.Companion.channelFirstOrNull
import top.yogiczy.mytv.core.data.entities.channel.ChannelGroupList.Companion.channelGroupIdx
import top.yogiczy.mytv.core.data.entities.channel.ChannelGroupList.Companion.channelIdx
import top.yogiczy.mytv.core.data.entities.channel.ChannelGroupList.Companion.channelLastOrNull
import top.yogiczy.mytv.core.data.entities.channel.ChannelGroupList.Companion.channelList
import top.yogiczy.mytv.core.data.entities.channel.ChannelLine
import top.yogiczy.mytv.core.data.entities.channel.ChannelLineList
import top.yogiczy.mytv.core.data.entities.channel.ChannelList
import top.yogiczy.mytv.core.data.entities.epg.EpgList
import top.yogiczy.mytv.core.data.entities.epg.EpgList.Companion.match
import top.yogiczy.mytv.core.data.entities.epg.EpgProgramme
import top.yogiczy.mytv.core.data.entities.epg.EpgProgrammeReserve
import top.yogiczy.mytv.core.data.entities.epg.EpgProgrammeReserveList
import top.yogiczy.mytv.core.data.utils.ChannelUtil
import top.yogiczy.mytv.core.data.utils.Constants
import top.yogiczy.mytv.core.data.utils.Loggable
import top.yogiczy.mytv.core.util.utils.urlHost
import top.yogiczy.mytv.tv.ui.material.Snackbar
import top.yogiczy.mytv.tv.ui.screen.settings.SettingsViewModel
import top.yogiczy.mytv.tv.ui.screen.settings.settingsVM
import top.yogiczy.mytv.tv.ui.screensold.videoplayer.VideoPlayerState
import top.yogiczy.mytv.tv.ui.screensold.videoplayer.player.VideoPlayer
import top.yogiczy.mytv.tv.ui.screensold.videoplayer.rememberVideoPlayerState
import kotlin.math.max
import kotlin.math.min

@Stable
class MainContentState(
    private val coroutineScope: CoroutineScope,
    private val videoPlayerState: VideoPlayerState,
    private val channelGroupListProvider: () -> ChannelGroupList = { ChannelGroupList() },
    private val favoriteChannelListProvider: () -> ChannelList = { ChannelList() },
    private val epgListProvider: () -> EpgList = { EpgList() },
    private val settingsViewModel: SettingsViewModel,
) : Loggable("MainContentState") {
    private var isInitialPlayback = true
    private var lastSavedPositionTime = 0L

    private var _currentChannel by mutableStateOf(Channel())
    val currentChannel get() = _currentChannel

    private var _currentChannelLineIdx by mutableIntStateOf(0)
    val currentChannelLineIdx get() = _currentChannelLineIdx

    val currentChannelLine get() = _currentChannel.lineList[_currentChannelLineIdx]

    private var _currentPlaybackEpgProgramme by mutableStateOf<EpgProgramme?>(null)
    val currentPlaybackEpgProgramme get() = _currentPlaybackEpgProgramme

    private var _tempChannelScreenHideJob: Job? = null

    private var _isTempChannelScreenVisible by mutableStateOf(false)
    var isTempChannelScreenVisible
        get() = _isTempChannelScreenVisible
        set(value) {
            _isTempChannelScreenVisible = value
        }

    private var _isChannelScreenVisible by mutableStateOf(false)
    var isChannelScreenVisible
        get() = _isChannelScreenVisible
        set(value) {
            _isChannelScreenVisible = value
        }

    private var _isVideoPlayerControllerScreenVisible by mutableStateOf(false)
    var isVideoPlayerControllerScreenVisible
        get() = _isVideoPlayerControllerScreenVisible
        set(value) {
            _isVideoPlayerControllerScreenVisible = value
        }

    private var _isQuickOpScreenVisible by mutableStateOf(false)
    var isQuickOpScreenVisible
        get() = _isQuickOpScreenVisible
        set(value) {
            _isQuickOpScreenVisible = value
        }

    private var _isEpgScreenVisible by mutableStateOf(false)
    var isEpgScreenVisible
        get() = _isEpgScreenVisible
        set(value) {
            _isEpgScreenVisible = value
        }

    private var _isChannelLineScreenVisible by mutableStateOf(false)
    var isChannelLineScreenVisible
        get() = _isChannelLineScreenVisible
        set(value) {
            _isChannelLineScreenVisible = value
        }

    private var _isVideoPlayerDisplayModeScreenVisible by mutableStateOf(false)
    var isVideoPlayerDisplayModeScreenVisible
        get() = _isVideoPlayerDisplayModeScreenVisible
        set(value) {
            _isVideoPlayerDisplayModeScreenVisible = value
        }

    private var _isVideoTracksScreenVisible by mutableStateOf(false)
    var isVideoTracksScreenVisible
        get() = _isVideoTracksScreenVisible
        set(value) {
            _isVideoTracksScreenVisible = value
        }

    private var _isAudioTracksScreenVisible by mutableStateOf(false)
    var isAudioTracksScreenVisible
        get() = _isAudioTracksScreenVisible
        set(value) {
            _isAudioTracksScreenVisible = value
        }

    private var _isSubtitleTracksScreenVisible by mutableStateOf(false)
    var isSubtitleTracksScreenVisible
        get() = _isSubtitleTracksScreenVisible
        set(value) {
            _isSubtitleTracksScreenVisible = value
        }

    init {
        val channelGroupList = channelGroupListProvider()

        // 如果回放节目太老了（超过10天），就直接直播
        val savedPlayback = settingsViewModel.iptvChannelLastPlaybackProgramme
        val isWayTooOld = savedPlayback?.let {
            System.currentTimeMillis() - it.startAt > 10L * 24 * 3600 * 1000
        } ?: false

        changeCurrentChannel(
            settingsViewModel.iptvChannelLastPlay.isEmptyOrElse {
                channelGroupList.channelFirstOrNull() ?: Channel.EMPTY
            },
            playbackEpgProgramme = if (isWayTooOld) null else savedPlayback,
        )

        videoPlayerState.onReady {
            settingsViewModel.iptvChannelLinePlayableUrlList += currentChannelLine.url
            settingsViewModel.iptvChannelLinePlayableHostList += currentChannelLine.url.urlHost()

            if (isInitialPlayback) {
                val savedPosition = settingsViewModel.iptvChannelLastPlaybackPosition
                if (savedPosition > 0) {
                    // 必须先重置，防止 seekTo 完成后再次触发 onReady 导致死循环
                    settingsViewModel.iptvChannelLastPlaybackPosition = 0

                    // 只有在进度没到最后 10 秒时才恢复
                    if (videoPlayerState.duration == 0L || savedPosition < videoPlayerState.duration - 10000) {
                        videoPlayerState.seekTo(savedPosition)
                    }
                }
                isInitialPlayback = false
            }
        }

        videoPlayerState.onError {
            if (_currentPlaybackEpgProgramme != null) {
                log.w("回放失败（可能已过期），正在自动切换到直播...")
                settingsViewModel.iptvChannelLastPlaybackProgramme = null
                settingsViewModel.iptvChannelLastPlaybackPosition = 0
                changeCurrentChannel(_currentChannel, _currentChannelLineIdx)
                return@onError
            }

            settingsViewModel.iptvChannelLinePlayableUrlList -= currentChannelLine.url
            settingsViewModel.iptvChannelLinePlayableHostList -= currentChannelLine.url.urlHost()

            if (_currentChannelLineIdx < _currentChannel.lineList.size - 1) {
                changeCurrentChannel(_currentChannel, _currentChannelLineIdx + 1)
            }
        }

        videoPlayerState.onInterrupt {
            changeCurrentChannel(
                _currentChannel,
                _currentChannelLineIdx,
                _currentPlaybackEpgProgramme
            )
        }

        videoPlayerState.onIsBuffering { isBuffering ->
            if (isBuffering) {
                _isTempChannelScreenVisible = true
            } else {
                _tempChannelScreenHideJob?.cancel()
                _tempChannelScreenHideJob = coroutineScope.launch {
                    val name = _currentChannel.name
                    val lineIdx = _currentChannelLineIdx
                    delay(Constants.UI_TEMP_CHANNEL_SCREEN_SHOW_DURATION)
                    if (name == _currentChannel.name && lineIdx == _currentChannelLineIdx) {
                        _isTempChannelScreenVisible = false
                    }
                }
            }
        }

        videoPlayerState.onPosition { position ->
            if (_currentPlaybackEpgProgramme != null) {
                // 进一步降低保存频率至 5 秒，减轻内存和 I/O 压力
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastSavedPositionTime > 5000) {
                    if (!videoPlayerState.isBuffering && videoPlayerState.isPlaying) {
                        // 如果快到结尾了（最后 10 秒），就不要保存进度了
                        if (videoPlayerState.duration > 0 && position > videoPlayerState.duration - 10000) {
                            settingsViewModel.iptvChannelLastPlaybackPosition = 0
                        } else {
                            settingsViewModel.iptvChannelLastPlaybackPosition = position
                        }
                        lastSavedPositionTime = currentTime
                    }
                }
            }
        }

        videoPlayerState.onEnded {
            if (_currentPlaybackEpgProgramme == null) return@onEnded

            val epg = epgListProvider().match(_currentChannel)
            val currentIdx = epg?.programmeList?.indexOf(_currentPlaybackEpgProgramme) ?: -1

            if (currentIdx != -1 && currentIdx < (epg?.programmeList?.size ?: 0) - 1) {
                val nextProgramme = epg!!.programmeList[currentIdx + 1]

                if (nextProgramme.endAt <= System.currentTimeMillis()) {
                    // 下一个节目的结束时间在过去，说明它是一个完整的回放节目
                    changeCurrentChannel(_currentChannel, _currentChannelLineIdx, nextProgramme)
                } else {
                    // 下一个节目结束时间在未来（正在直播或未开始），返回直播
                    changeCurrentChannel(_currentChannel, _currentChannelLineIdx)
                }
            } else {
                // 已经是最后一个节目或没找到，返回直播
                changeCurrentChannel(_currentChannel, _currentChannelLineIdx)
            }
        }
    }

    private fun getPrevFavoriteChannel(): Channel? {
        if (!settingsViewModel.iptvChannelFavoriteListVisible) return null

        val channelGroupList = channelGroupListProvider()
        val favoriteChannelList = favoriteChannelListProvider()

        if (_currentChannel !in favoriteChannelList) return null

        val currentIdx = favoriteChannelList.indexOf(_currentChannel)

        return favoriteChannelList.getOrElse(currentIdx - 1) {
            if (settingsViewModel.iptvChannelChangeListLoop) favoriteChannelList.lastOrNull()
            else channelGroupList.channelLastOrNull()
        }
    }

    private fun getNextFavoriteChannel(): Channel? {
        if (!settingsViewModel.iptvChannelFavoriteListVisible) return null

        val channelGroupList = channelGroupListProvider()
        val favoriteChannelList = favoriteChannelListProvider()

        if (_currentChannel !in favoriteChannelList) return null

        val currentIdx = favoriteChannelList.indexOf(_currentChannel)

        return favoriteChannelList.getOrElse(currentIdx + 1) {
            if (settingsViewModel.iptvChannelChangeListLoop) favoriteChannelList.firstOrNull()
            else channelGroupList.channelFirstOrNull()
        }
    }

    private fun getPrevChannel(): Channel {
        return getPrevFavoriteChannel() ?: run {
            val channelGroupList = channelGroupListProvider()
            return if (settingsViewModel.iptvChannelChangeListLoop) {
                val group =
                    channelGroupList.getOrElse(channelGroupList.channelGroupIdx(_currentChannel)) { channelGroupList.first() }
                val currentIdx = group.channelList.indexOf(_currentChannel)
                group.channelList.getOrElse(currentIdx - 1) { group.channelList.last() }
            } else {
                val currentIdx = channelGroupList.channelIdx(_currentChannel)
                channelGroupList.channelList.getOrElse(currentIdx - 1) {
                    channelGroupList.channelLastOrNull() ?: Channel()
                }
            }
        }
    }

    private fun getNextChannel(): Channel {
        return getNextFavoriteChannel() ?: run {
            val channelGroupList = channelGroupListProvider()
            return if (settingsViewModel.iptvChannelChangeListLoop) {
                val group =
                    channelGroupList.getOrElse(channelGroupList.channelGroupIdx(_currentChannel)) { channelGroupList.first() }
                val currentIdx = group.channelList.indexOf(_currentChannel)
                group.channelList.getOrElse(currentIdx + 1) { group.channelList.first() }
            } else {
                val currentIdx = channelGroupList.channelIdx(_currentChannel)
                channelGroupList.channelList.getOrElse(currentIdx + 1) {
                    channelGroupList.channelFirstOrNull() ?: Channel()
                }
            }
        }
    }

    private fun getLineIdx(lineList: ChannelLineList, lineIdx: Int? = null): Int {
        val idx = if (lineIdx == null) {
            val idx = lineList.indexOfFirst { line ->
                settingsViewModel.iptvChannelLinePlayableUrlList.contains(line.url)
            }

            if (idx < 0) {
                lineList.indexOfFirst { line ->
                    settingsViewModel.iptvChannelLinePlayableHostList.contains(line.url.urlHost())
                }
            } else idx
        } else (lineIdx + lineList.size) % lineList.size

        return max(0, min(idx, lineList.size - 1))
    }

    fun changeCurrentChannel(
        channel: Channel,
        lineIdx: Int? = null,
        playbackEpgProgramme: EpgProgramme? = null,
    ) {
        isInitialPlayback = true

        // 如果选择的是新节目，重置保存的进度
        if (playbackEpgProgramme != settingsViewModel.iptvChannelLastPlaybackProgramme) {
            settingsViewModel.iptvChannelLastPlaybackPosition = 0
        }

        settingsViewModel.iptvChannelLastPlay = channel
        settingsViewModel.iptvChannelLastPlaybackProgramme = playbackEpgProgramme

        if (channel == _currentChannel && lineIdx == _currentChannelLineIdx && playbackEpgProgramme == _currentPlaybackEpgProgramme) return

        if (channel == _currentChannel && lineIdx != _currentChannelLineIdx) {
            settingsViewModel.iptvChannelLinePlayableUrlList -= currentChannelLine.url
            settingsViewModel.iptvChannelLinePlayableHostList -= currentChannelLine.url.urlHost()
        }

        _isTempChannelScreenVisible = true

        _currentChannel = channel
        _currentChannelLineIdx = getLineIdx(_currentChannel.lineList, lineIdx)

        _currentPlaybackEpgProgramme = playbackEpgProgramme

        val line = if (_currentPlaybackEpgProgramme != null) {
            currentChannelLine.copy(
                url = ChannelUtil.getPlaybackUrl(
                    currentChannelLine,
                    _currentPlaybackEpgProgramme!!.startAt,
                    _currentPlaybackEpgProgramme!!.endAt,
                )
            )
        } else {
            currentChannelLine
        }

        log.d("播放${_currentChannel.name}（${_currentChannelLineIdx + 1}/${_currentChannel.lineList.size}）: $line")

        if (line.url.startsWith("webview://")) {
            videoPlayerState.metadata = VideoPlayer.Metadata()
            videoPlayerState.stop()
        } else {
            videoPlayerState.prepare(line)
        }
    }

    fun changeCurrentChannelToPrev() {
        changeCurrentChannel(getPrevChannel())
    }

    fun changeCurrentChannelToNext() {
        changeCurrentChannel(getNextChannel())
    }

    fun reverseEpgProgrammeOrNot(channel: Channel, programme: EpgProgramme) {
        val reverse = settingsViewModel.epgChannelReserveList.firstOrNull {
            it.test(channel, programme)
        }

        if (reverse != null) {
            settingsViewModel.epgChannelReserveList =
                EpgProgrammeReserveList(settingsViewModel.epgChannelReserveList - reverse)
            Snackbar.show("取消预约：${reverse.channel} - ${reverse.programme}")
        } else {
            val newReserve = EpgProgrammeReserve(
                channel = channel.name,
                programme = programme.title,
                startAt = programme.startAt,
                endAt = programme.endAt,
            )

            settingsViewModel.epgChannelReserveList =
                EpgProgrammeReserveList(settingsViewModel.epgChannelReserveList + newReserve)
            Snackbar.show("已预约：${channel.name} - ${programme.title}")
        }
    }

    fun supportPlayback(
        channel: Channel = _currentChannel,
        lineIdx: Int? = _currentChannelLineIdx,
    ): Boolean {
        val currentLineIdx = getLineIdx(channel.lineList, lineIdx)
        return ChannelUtil.urlSupportPlayback(channel.lineList[currentLineIdx])
    }
}

@Composable
fun rememberMainContentState(
    coroutineScope: CoroutineScope = rememberCoroutineScope(),
    videoPlayerState: VideoPlayerState = rememberVideoPlayerState(),
    channelGroupListProvider: () -> ChannelGroupList = { ChannelGroupList() },
    favoriteChannelListProvider: () -> ChannelList = { ChannelList() },
    epgListProvider: () -> EpgList = { EpgList() },
    settingsViewModel: SettingsViewModel = settingsVM,
): MainContentState {
    val channelGroupListProviderUpdated by rememberUpdatedState(channelGroupListProvider)
    val favoriteChannelListProviderUpdated by rememberUpdatedState(favoriteChannelListProvider)
    val epgListProviderUpdated by rememberUpdatedState(epgListProvider)

    return remember(settingsVM.videoPlayerCore) {
        MainContentState(
            coroutineScope = coroutineScope,
            videoPlayerState = videoPlayerState,
            channelGroupListProvider = channelGroupListProviderUpdated,
            favoriteChannelListProvider = favoriteChannelListProviderUpdated,
            epgListProvider = epgListProviderUpdated,
            settingsViewModel = settingsViewModel,
        )
    }
}
