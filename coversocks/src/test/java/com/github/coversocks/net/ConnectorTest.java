package com.github.coversocks.net;

import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Arrays;
import java.util.Iterator;

public class ConnectorTest {
    Thread tcpThread;
    Selector tcpSelector;

    void runTCPServer() throws IOException {
        Selector selector = Selector.open();
        ServerSocketChannel channel = ServerSocketChannel.open();
        channel.configureBlocking(false);
        channel.bind(new InetSocketAddress("127.0.0.1", 10234));
        channel.register(selector, SelectionKey.OP_ACCEPT);
        this.tcpSelector = selector;
        this.tcpThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    ByteBuffer readBuffer = ByteBuffer.allocate(10240);
                    while (true) {
                        int s = tcpSelector.select();
                        if (!tcpSelector.isOpen()) {
                            break;
                        }
                        System.out.println("selecting " + s);
                        Iterator<SelectionKey> keys = tcpSelector.selectedKeys().iterator();
                        while (keys.hasNext()) {
                            SelectionKey key = keys.next();
                            keys.remove();
                            if (key.isAcceptable()) {
                                SocketChannel c = ((ServerSocketChannel) key.channel()).accept();
                                c.configureBlocking(false);
                                c.register(tcpSelector, SelectionKey.OP_READ);
                            } else if (key.isReadable()) {
                                SocketChannel c = (SocketChannel) key.channel();
                                readBuffer.clear();
                                int n = c.read(readBuffer);
                                if (n < 1) {
                                    key.cancel();
                                    c.close();
                                } else {
                                    readBuffer.flip();
                                    c.write(readBuffer);
                                }
                            } else {
                                key.cancel();
                                key.channel().close();
                            }
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
                System.out.println("tcp server is done");
            }
        });
        this.tcpThread.start();
    }

    Thread udpThread;
    Selector udpSelector;

    void runUDPServer() throws IOException {
        Selector selector = Selector.open();
        DatagramChannel channel = DatagramChannel.open();
        channel.bind(new InetSocketAddress(10235));
        channel.configureBlocking(false);
        channel.register(selector, SelectionKey.OP_READ);
        this.udpSelector = selector;
        this.udpThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    ByteBuffer readBuffer = ByteBuffer.allocate(10240);
                    while (true) {
                        int s = udpSelector.select();
                        if (!udpSelector.isOpen()) {
                            break;
                        }
                        System.out.println("selecting " + s);
                        Iterator<SelectionKey> keys = udpSelector.selectedKeys().iterator();
                        while (keys.hasNext()) {
                            SelectionKey key = keys.next();
                            keys.remove();
                            if (key.isReadable()) {
                                DatagramChannel c = (DatagramChannel) key.channel();
                                readBuffer.clear();
                                SocketAddress from = c.receive(readBuffer);
                                readBuffer.flip();
                                c.send(readBuffer, from);
                            } else {
                                key.cancel();
                                key.channel().close();
                            }
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
        this.udpThread.start();
    }

    Object waiter = new Object();
    Handler handler = new Handler() {
        @Override
        public void channelOpened(SelectableChannel channel) {

        }

        @Override
        public void channelFail(SelectionKey key, Exception e) {
            synchronized (waiter) {
                waiter.notify();
            }
        }

        @Override
        public void channelConnected(SelectionKey key) {
            try {
                Object userdata = key.attachment();
                connector.write(userdata, ByteBuffer.wrap("testing".getBytes()));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void channelClosed(SelectionKey key, Exception e) {
            e.printStackTrace();
        }

        @Override
        public void receiveData(SelectionKey key, ByteBuffer buffer) {
            try {
                byte[] data = Arrays.copyOfRange(buffer.array(), buffer.arrayOffset(), buffer.limit());
                System.out.println(new String(data, "UTF-8"));
                synchronized (waiter) {
                    waiter.notify();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    };
    Connector connector;

    @Test
    public void testTCP() throws IOException, InterruptedException, URISyntaxException {
        connector = new Connector(handler, 1400);
        this.runTCPServer();
        Thread runner = new Thread(connector);
        runner.start();
        connector.connect(1, "tcp://127.0.0.1:10234");
        synchronized (this.waiter) {
            this.waiter.wait();
        }
        tcpSelector.close();
        tcpThread.join();
        connector.close();
        runner.join();
    }

    @Test
    public void testUDP() throws IOException, InterruptedException, URISyntaxException {
        connector = new Connector(handler, 1400);
        this.runUDPServer();
        Thread runner = new Thread(connector);
        runner.start();
        connector.connect(1, "udp://127.0.0.1:10235");
        synchronized (this.waiter) {
            this.waiter.wait();
        }
        udpSelector.close();
        udpThread.join();
        connector.close();
        runner.join();
    }

    @Test
    public void testError() {
        try {
            connector.connect(1, "xxx://xxx:100");
        } catch (Exception e) {
            Assert.assertTrue(e.getMessage().contains("not supported protocol"));
        }
    }

    @Test
    public void testFail() throws IOException, URISyntaxException, InterruptedException {
        Thread runner = new Thread(connector);
        runner.start();
        connector.connect(1, "tcp://127.0.0.1:10135");
        synchronized (this.waiter) {
            this.waiter.wait();
        }
        connector.close();
        runner.join();
    }

    Handler benchHandler = new Handler() {
        @Override
        public void channelOpened(SelectableChannel channel) {

        }

        @Override
        public void channelFail(SelectionKey key, Exception e) {

        }

        @Override
        public void channelConnected(SelectionKey key) {
            try {
                SocketChannel channel = (SocketChannel) key.channel();
                channel.write(ByteBuffer.wrap("abccccskfsdfskfjsabccccskfsdfskfjsabccccskfsdfskfjsabccccskfsdfskfjsabccccskfsdfskfjsabccccskfsdfskfjsabccccskfsdfskfjs".getBytes()));
            } catch (Exception e) {

            }
        }

        @Override
        public void channelClosed(SelectionKey key, Exception e) {

        }

        @Override
        public void receiveData(SelectionKey key, ByteBuffer buffer) {
            try {
                SocketChannel channel = (SocketChannel) key.channel();
                channel.write(buffer);
            } catch (Exception e) {

            }

        }
    };

    @Test
    public void testBench() throws IOException, URISyntaxException {
        Connector connector;
        //
        connector= new Connector(this.benchHandler, 1024000);
        connector.connect(1, "tcp://127.0.0.1:8090");
        new Thread(connector).start();
        //
        connector= new Connector(this.benchHandler, 1024000);
        connector.connect(1, "tcp://127.0.0.1:8090");
        new Thread(connector).start();
        //
        connector= new Connector(this.benchHandler, 1024000);
        connector.connect(1, "tcp://127.0.0.1:8090");
        connector.run();
    }
}
