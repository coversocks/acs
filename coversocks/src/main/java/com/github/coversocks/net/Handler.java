package com.github.coversocks.net;


import java.nio.ByteBuffer;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;

/**
 * the Connector callback handler
 */
public interface Handler {
    /**
     * the callback when channel is opened.
     *
     * @param channel the opened channel
     */
    void channelOpened(SelectableChannel channel);

    /**
     * the callback when channel is connect fail.
     *
     * @param key
     */
    void channelFail(SelectionKey key, Exception e);

    /**
     * the callback when channel is connected.
     *
     * @param key
     */
    void channelConnected(SelectionKey key);

    /**
     * the callback when channel is closed.
     *
     * @param key the channel selection key
     * @param e   the close exception
     */
    void channelClosed(SelectionKey key, Exception e);

    /**
     * the callback when channel receive data.
     *
     * @param key    the channel selection key
     * @param buffer the data buffer.
     */
    void receiveData(SelectionKey key, ByteBuffer buffer);
}
