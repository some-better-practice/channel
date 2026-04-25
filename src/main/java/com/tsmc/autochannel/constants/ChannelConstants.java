package com.tsmc.autochannel.constants;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class ChannelConstants {

    public static final List<ChannelInfo> CHANNEL_INFOS = List.of(
            new ChannelInfo("CH001", "Channel Alpha",   "mock_nas/source/alpha",   "mock_nas/destination/alpha"),
            new ChannelInfo("CH002", "Channel Beta",    "mock_nas/source/beta",    "mock_nas/destination/beta"),
            new ChannelInfo("CH003", "Channel Gamma",   "mock_nas/source/gamma",   "mock_nas/destination/gamma"),
            new ChannelInfo("CH004", "Channel Delta",   "mock_nas/source/delta",   "mock_nas/destination/delta"),
            new ChannelInfo("CH005", "Channel Epsilon", "mock_nas/source/epsilon", "mock_nas/destination/epsilon")
    );

    public static ChannelInfo randomChannel() {
        return CHANNEL_INFOS.get(ThreadLocalRandom.current().nextInt(CHANNEL_INFOS.size()));
    }
}
