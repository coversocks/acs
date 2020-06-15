package com.github.coversocks;

import android.content.Intent;
import android.net.VpnService;
import android.os.ParcelFileDescriptor;

import com.github.coversocks.net.Connector;
import com.github.coversocks.net.Handler;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.Arrays;
import java.util.logging.Level;


public class NetVpnService extends VpnService {
    private ParcelFileDescriptor mInterface;
    Builder builder = new Builder();

    // Services interface
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        this.bootstrap();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    private InputStream in;
    private OutputStream out;

    private Handler handler = new XHandler();

    class XHandler implements Handler {

        byte[] buf = new byte[15000];
        int having = 0;

        @Override
        public void channelOpened(SelectableChannel channel) {
            SocketChannel c = (SocketChannel) channel;
            protect(c.socket());
        }

        @Override
        public void channelFail(SelectionKey key, Exception e) {
            System.out.println("fail");
        }

        @Override
        public void channelConnected(SelectionKey key) {
            System.out.println("connected");
            new Thread(reader).start();
        }

        @Override
        public void channelClosed(SelectionKey key, Exception e) {
            System.out.println("closed");
        }

        @Override
        public void receiveData(SelectionKey key, ByteBuffer buffer) {
            int last = 0;
            int header = 0;
            last = having;
            try {
                System.arraycopy(buffer.array(), buffer.arrayOffset(), buf, having, buffer.limit());
                having += buffer.limit();
                while (true) {
                    buf[0] = 0;
                    header = ByteBuffer.wrap(buf, 0, 4).getInt();
                    if (header > 1505) {
                        throw new IOException("too large");
                    }
                    if (having < header) {
                        return;
                    }
//            System.out.println("read header " + header);
                    byte hex = 0;
                    for (int i = 4; i < header - 1; i++) {
                        hex += buf[i];
                    }
                    if (hex != buf[header - 1]) {
                        throw new IOException("invalid");
                    }
                    out.write(buf, 4, header - 4);
                    if (having - header > 0) {
//                    System.arraycopy(buf, header, buf, 0, having - header);
                        for (int i = 0; i < having - header; i++) {
                            buf[i] = buf[header + i];
                        }
                        buf[0] = 0;
                        int nh = ByteBuffer.wrap(buf, 0, 4).getInt();
                        if (nh > 1505) {
                            throw new IOException("err");
                        }
                    }
                    having -= header;
                }
            } catch (Exception e) {
                System.out.println("receive " + concat(buf, 0, having, " "));
                e.printStackTrace();
            }
        }
    }

    private String concat(final byte[] bytes, final int offset, int length, final String str) {
        final StringBuilder sb = new StringBuilder();
        for (int i = offset; i < offset + length; i++) {
            sb.append(bytes[i] & 0xFF);
            sb.append(str);
        }
        return sb.toString();
    }

    private Connector connector = new Connector(this.handler, 1500);

    private Runnable reader = new Runnable() {
        @Override
        public void run() {
            try {
                byte[] buf = new byte[1600];
                while (true) {
                    int n = in.read(buf, 4, 1500);
                    if (n < 1) {
                        continue;
                    }
                    try {
                        ByteBuffer.wrap(buf, 0, 4).putInt(n + 4);
                        buf[0] = 0;
//                        System.out.println("send " + n);
                        ByteBuffer t = ByteBuffer.wrap(buf, 0, n + 4);
                        connector.write(1, t);
                    } catch (Exception e1) {
                        e1.printStackTrace();
                    }
                }
            } catch (Exception e) {

            }
        }
    };

    private Runnable conn = new Runnable() {
        @Override
        public void run() {
            try {
                connector.connect(1, "tcp://192.168.1.54:8091");
                connector.run();
            } catch (IOException e) {
                e.printStackTrace();
            } catch (URISyntaxException e) {
                e.printStackTrace();
            }
        }
    };

    private void bootstrap() {
        try {
            mInterface = builder.setSession("Coversocks")
                    .setMtu(1500)
                    .addAddress("172.23.0.1", 24)
                    .addDnsServer("114.114.114.114")
                    .addRoute("0.0.0.0", 0).establish();
            this.in = new FileInputStream(mInterface.getFileDescriptor());
            this.out = new FileOutputStream(mInterface.getFileDescriptor());
            new Thread(conn).start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
