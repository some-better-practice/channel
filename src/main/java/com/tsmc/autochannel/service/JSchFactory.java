package com.tsmc.autochannel.service;

import com.jcraft.jsch.JSch;

@FunctionalInterface
public interface JSchFactory {
    JSch create();
}
