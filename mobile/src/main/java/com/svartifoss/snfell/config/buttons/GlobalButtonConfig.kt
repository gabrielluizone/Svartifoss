package com.svartifoss.snfell.config.buttons

import android.util.ArrayMap
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.google.auto.factory.AutoFactory
import com.google.auto.factory.Provided
import com.svartifoss.snfell.actions.PhoneAction
import com.svartifoss.snfell.common.CommPaths
import com.svartifoss.snfell.common.buttonconfig.ButtonInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

@AutoFactory
class GlobalButtonConfig
constructor(private val playbackConfig: Boolean,
            @Provided diskButtonConfigStorageFactory: DiskButtonConfigStorageFactory,
            @Provided buttonConfigTransmitterFactory: ButtonConfigTransmitterFactory) : ButtonConfig {
    companion object {
        private val mutablePlayingConfigSaved = MutableLiveData<Unit>()
        private val mutableStoppedConfigSaved = MutableLiveData<Unit>()

        /** Emitted only after the playing config is safely available to disk-based previews. */
        val playingConfigSaved: LiveData<Unit> = mutablePlayingConfigSaved
        /** Emitted after the stopped/paused action config reaches disk-based previews. */
        val stoppedConfigSaved: LiveData<Unit> = mutableStoppedConfigSaved
    }

    private val configMap = ArrayMap<ButtonInfo, PhoneAction>()
    private var commiting: Boolean = false
    private var commitAgain: Boolean = false

    private val diskButtonStorage: DiskButtonConfigStorage
    private val buttonTransmitter: ButtonConfigTransmitter

    override fun saveButtonAction(buttonInfo: ButtonInfo, action: PhoneAction?) {
        if (action == null) {
            configMap.remove(buttonInfo)
        } else {
            configMap[buttonInfo] = action
        }
    }

    override fun getScreenAction(buttonInfo: ButtonInfo): PhoneAction? {
        return configMap[buttonInfo] ?: configMap[buttonInfo.getLegacyButtonInfo()]
    }

    override fun getAllActions(): Collection<Map.Entry<ButtonInfo, PhoneAction>> {
        return configMap.entries
    }

    init {
        val diskSuffix: String
        val watchSenderPath: String

        if (playbackConfig) {
            diskSuffix = "_playing"
            watchSenderPath = CommPaths.DATA_PLAYING_ACTION_CONFIG
        } else {
            diskSuffix = "_stopped"
            watchSenderPath = CommPaths.DATA_STOPPING_ACTION_CONFIG
        }

        diskButtonStorage = diskButtonConfigStorageFactory.create(diskSuffix)
        diskButtonStorage.loadButtons(this)

        buttonTransmitter = buttonConfigTransmitterFactory.create(this, watchSenderPath)
    }


    override fun commit() {
        if (commiting) {
            commitAgain = true
            return
        }

        commiting = true

        GlobalScope.launch(Dispatchers.Main) {
            try {
                withContext(Dispatchers.Default) worker@ {
                    val saved = diskButtonStorage.saveButtons(configMap.entries)
                    if (saved) {
                        if (playbackConfig) {
                            mutablePlayingConfigSaved.postValue(Unit)
                        } else {
                            mutableStoppedConfigSaved.postValue(Unit)
                        }
                    }
                    buttonTransmitter.sendConfigToWatch(configMap.entries)
                }
            } catch (e: Exception) {
                Timber.e(e, "Could not commit button configuration")
            } finally {
                commiting = false
                val runQueuedCommit = commitAgain
                commitAgain = false
                if (runQueuedCommit) {
                    commit()
                }
            }
        }
    }
}
