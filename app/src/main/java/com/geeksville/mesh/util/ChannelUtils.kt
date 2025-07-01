package com.geeksville.mesh.util

import com.geeksville.mesh.ChannelProtos
import com.geeksville.mesh.channel
import com.geeksville.mesh.channelSettings

/**
 * Builds a [Channel] list from the difference between two [ChannelSettings] lists.
 * Only changes are included in the resulting list.
 *
 * @param new The updated [ChannelSettings] list.
 * @param old The current [ChannelSettings] list (required when disabling unused channels).
 * @return A [Channel] list containing only the modified channels.
 */
internal fun getChannelList(
    new: List<ChannelProtos.ChannelSettings>,
    old: List<ChannelProtos.ChannelSettings>,
): List<ChannelProtos.Channel> = buildList {
    for (i in 0..maxOf(old.lastIndex, new.lastIndex)) {
        if (old.getOrNull(i) != new.getOrNull(i)) {
            add(
                channel {
                    role = when (i) {
                        0 -> ChannelProtos.Channel.Role.PRIMARY
                        in 1..new.lastIndex -> ChannelProtos.Channel.Role.SECONDARY
                        else -> ChannelProtos.Channel.Role.DISABLED
                    }
                    index = i
                    settings = new.getOrNull(i) ?: channelSettings { }
                }
            )
        }
    }
}
