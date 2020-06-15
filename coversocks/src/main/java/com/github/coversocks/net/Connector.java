package com.github.coversocks.net;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * the Connector provider tcp/udp connect by java nio.
 * it will callback Handler.channelOpened when connection is opened,
 * it will callback Handler.channelConnected when connection is connected,
 * it will callback Handler.channelClosed when connection is closed,
 * it will callback Handler.receiveData when connection data is received.
 */
public class Connector implements Runnable {
    private final Logger log = Logger.getLogger(Connector.class.getName());

    /**
     * RegisterPair is inner class for storing register task.
     */
    class RegisterPair {
        public SelectableChannel channel;
        public int opts;
        public Object userdata;

        public RegisterPair(SelectableChannel channel, int opts, Object userdata) {
            this.channel = channel;
            this.opts = opts;
            this.userdata = userdata;
        }
    }

    Handler handler;
    Selector selector;
    Map<Object, SelectionKey> keys = new ConcurrentHashMap<>();
    ByteBuffer readBuffer;
    ConcurrentLinkedQueue registers = new ConcurrentLinkedQueue();

    /**
     * the constructor to create new Connector
     *
     * @param handler the callback handler
     * @param mtu     the MTU.
     */
    public Connector(Handler handler, int mtu) {
        this.handler = handler;
        this.readBuffer = ByteBuffer.allocate(mtu);
        try {
            this.selector = Selector.open();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    /**
     * close the selector.
     *
     * @throws IOException error exception
     */
    public void close() throws IOException {
        if (this.selector != null) {
            this.selector.close();
        }
    }

    @Override
    public void run() {
        try {
            while (true) {
                this.selector.select();
                if (!this.selector.isOpen()) {
                    break;
                }
                this.process();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * the main process to run the selector.
     *
     * @throws IOException error exception.
     */
    protected void process() throws IOException {
        Iterator regs = this.registers.iterator();
        while (regs.hasNext()) {
            RegisterPair reg = (RegisterPair) regs.next();
            regs.remove();
            SelectionKey key = reg.channel.register(this.selector, reg.opts);
            key.attach(reg.userdata);
            this.keys.put(reg.userdata, key);
            if (reg.channel instanceof DatagramChannel) {
                this.handler.channelConnected(key);
            }
        }
        Iterator keys = this.selector.selectedKeys().iterator();
        while (keys.hasNext()) {
            SelectionKey key = (SelectionKey) keys.next();
            keys.remove();
            if (!key.isValid()) {
                continue;
            }
            if (key.isConnectable()) {
                this.finishConnection(key);
            } else if (key.isReadable()) {
                this.readConnection(key);
            }
        }
    }

    /**
     * find connected connection.
     *
     * @param key the channel key
     * @throws IOException error exception.
     */
    protected void finishConnection(SelectionKey key) throws IOException {
        SocketChannel channel = (SocketChannel) key.channel();
        try {
            channel.finishConnect();
            key.interestOps(SelectionKey.OP_READ);
            this.handler.channelConnected(key);
        } catch (IOException e) {
            this.log.log(Level.INFO, "connection " + key.attachment() + " is closed");
            this.handler.channelFail(key, e);
            key.cancel();
            channel.close();
            this.keys.remove(key.attachment());
        }
    }

    private static String convertToHex(byte[] data) {
        StringBuilder buf = new StringBuilder();
        for (byte b : data) {
            int halfbyte = (b >>> 4) & 0x0F;
            int two_halfs = 0;
            do {
                buf.append((0 <= halfbyte) && (halfbyte <= 9) ? (char) ('0' + halfbyte) : (char) ('a' + (halfbyte - 10)));
                halfbyte = b & 0x0F;
            } while (two_halfs++ < 1);
        }
        return buf.toString();
    }

    /**
     * read readable connection.
     *
     * @param key the channel key
     * @throws IOException error exception.
     */
    protected void readConnection(SelectionKey key) throws IOException {
        SelectableChannel channel = key.channel();
        try {
            int n;
            this.readBuffer.clear();
            if (channel instanceof SocketChannel) {
                SocketChannel tcp = (SocketChannel) channel;
                n = tcp.read(this.readBuffer);
            } else {
                DatagramChannel udp = (DatagramChannel) channel;
                n = udp.read(this.readBuffer);
            }
            if (n > 0) {
                this.readBuffer.flip();
                this.handler.receiveData(key, this.readBuffer);
            } else {
                throw new IOException("read return " + n);
            }
        } catch (IOException e) {
            this.channelClosed(key, e);
        }
    }

    private void channelClosed(SelectionKey key, Exception e) throws IOException {
        this.log.log(Level.INFO, "connection " + key.attachment() + " is closed");
        this.handler.channelClosed(key, e);
        key.cancel();
        key.channel().close();
        this.keys.remove(key.attachment());
    }

    /**
     * add connect task to list,
     * it will callback Handler.channelOpened when connection is opened,
     * it will callback Handler.channelConnected when connection is connected,
     * it will callback Handler.channelClosed when connection is closed,
     * it will callback Handler.receiveData when connection data is received.
     *
     * @param userdata the userdata to attach on connection
     * @param remote   the remote uri.
     * @throws IOException        the io error exception.
     * @throws URISyntaxException the uri error exception.
     */
    public void connect(Object userdata, String remote) throws IOException, URISyntaxException {
        this.log.log(Level.INFO, "start connect to " + remote + " by id " + userdata);
        URI url = new URI(remote);
        SocketAddress address = null;
        switch (url.getScheme()) {
            case "tcp":
                SocketChannel tcp = SocketChannel.open();
                tcp.configureBlocking(false);
                this.handler.channelOpened(tcp);
                address = new InetSocketAddress(url.getHost(), url.getPort());
                tcp.connect(address);
                this.registers.add(new RegisterPair(tcp, SelectionKey.OP_CONNECT, userdata));
                this.selector.wakeup();
                break;
            case "dns":
                address = new InetSocketAddress("114.114.114.114", 53);
            case "udp":
                if (address == null) {
                    address = new InetSocketAddress(url.getHost(), url.getPort());
                }
                DatagramChannel udp = DatagramChannel.open();
                udp.configureBlocking(false);
                this.handler.channelOpened(udp);
                udp.connect(address);
                this.registers.add(new RegisterPair(udp, SelectionKey.OP_READ, userdata));
                this.selector.wakeup();
                break;
            default:
                throw new IOException("not supported protocol" + url.getScheme());
        }
    }

    /**
     * send data to connection by userdata
     *
     * @param userdata the userdata to get connection.
     * @param buffer   the data to send
     * @return send length
     * @throws IOException the io error exception.
     */
    public int write(Object userdata, ByteBuffer buffer) throws IOException {
        SelectionKey key = this.keys.get(userdata);
        if (key == null) {
            return 0;
        }
        SelectableChannel channel = key.channel();
        if (channel instanceof SocketChannel) {
            SocketChannel tcp = (SocketChannel) channel;
            return tcp.write(buffer);
        } else {
            DatagramChannel udp = (DatagramChannel) channel;
            return udp.write(buffer);
        }
    }

    /**
     * close the connection by userdata.
     *
     * @param userdata the userdata to get connection.
     * @throws IOException the io error exception.
     */
    public void close(Object userdata) throws IOException {
        SelectionKey key = this.keys.get(userdata);
        if (key != null) {
            this.channelClosed(key, new IOException("user closed"));
        }
    }
}
