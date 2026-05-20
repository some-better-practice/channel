package com.tsmc.autochannel.component;

import java.io.IOException;

@FunctionalInterface
public interface ProcessStarter {
    Process start(ProcessBuilder builder) throws IOException;
}
