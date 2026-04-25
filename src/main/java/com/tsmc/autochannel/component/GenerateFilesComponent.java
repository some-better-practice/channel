package com.tsmc.autochannel.component;

import com.tsmc.autochannel.constants.ChannelConstants;
import com.tsmc.autochannel.constants.ChannelInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Component
public class GenerateFilesComponent {

    public void execute(int count) {
        log.info("[generateFiles] Generating {} file(s) into mock_nas/source/...", count);

        for (int i = 0; i < count; i++) {
            ChannelInfo channel = ChannelConstants.randomChannel();
            Path sourcePath = Path.of(channel.sourcePath());

            try {
                Files.createDirectories(sourcePath);

                String filename = "data_" + System.currentTimeMillis() + "_" + i + ".stdf";
                Path file = sourcePath.resolve(filename);

                byte[] data = new byte[ThreadLocalRandom.current().nextInt(1024, 512 * 1024)];
                ThreadLocalRandom.current().nextBytes(data);
                Files.write(file, data);

                log.info("[generateFiles] Created {} (channelId={}, channel={})", file, channel.channelId(), channel.name());
            } catch (Exception e) {
                log.error("[generateFiles] Failed to create file for channel {}: {}", channel.channelId(), e.getMessage(), e);
            }
        }

        log.info("[generateFiles] Done — {} file(s) generated", count);
    }
}
