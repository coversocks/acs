package com.github.coversocks;

import android.content.Intent;
import android.net.VpnService;
import android.os.Binder;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;

import coversocks.Coversocks;

import com.github.coversocks.net.Connector;
import com.github.coversocks.net.Handler;

public class CoverService extends VpnService {
    private final Logger log = Logger.getLogger(CoverService.class.getName());
    private final int MTU = 1500;
    private ParcelFileDescriptor mInterface;
    Builder builder = new Builder();

    private static final byte MessageHeadConn = 10;
    private static final byte MessageHeadBack = 20;
    private static final byte MessageHeadData = 30;
    private static final byte MessageHeadClose = 40;

    public static void testWeb(String url, String digest) {
        Coversocks.testWeb(url, digest);
    }

    public static String changeMode(String mode) {
        return Coversocks.changeMode(mode);
    }

    public static String proxyMode() {
        return Coversocks.proxyMode();
    }

    public static String proxySet(String key, boolean proxy) {
        return Coversocks.proxySet(key, proxy);
    }


    public static void hello() {
        Coversocks.hello();
    }

    IBinder mBinder = new LocalBinder();

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    public class LocalBinder extends Binder {
        public CoverService getServerInstance() {
            return CoverService.this;
        }
    }

    public boolean isRunning() {
        return mInterface != null;
    }

    // Services interface
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        this.bootstrap();
        return START_STICKY;
    }

    public void stop() {
        messageRunning = false;
        Coversocks.stop();
        try {
            this.mInterface.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (this.messageReader != null) {
            try {
                this.messageReader.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        if (this.connectorProcessor != null) {
            try {
                this.connector.close();
                this.connectorProcessor.join();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        this.mInterface = null;
        this.messageReader = null;
        this.connectorProcessor = null;
        this.connector = null;
        stopSelf();
    }


    @Override
    public void onDestroy() {
        super.onDestroy();
    }


    private void messageWriteBack(long cid, byte[] message) {
        int len = 10;
        if (message != null) {
            len += message.length;
        }
        byte[] data = new byte[len];
        ByteBuffer buf = ByteBuffer.wrap(data);
        buf.put((byte) 'm');
        buf.putLong(cid);
        buf.put(MessageHeadBack);
        if (message != null) {
            buf.put(message);
        }
        buf.flip();
        Coversocks.writeMessage(data);
    }

    private void messageWriteClose(long cid) {
        log.log(Level.INFO, "message write close to " + cid);
        byte[] data = new byte[10];
        ByteBuffer buf = ByteBuffer.wrap(data);
        buf.put((byte) 'm');
        buf.putLong(cid);
        buf.put(MessageHeadClose);
        buf.flip();
        Coversocks.writeMessage(data);
    }

    private void messageWriteData(long cid, ByteBuffer buffer) {
        int length = buffer.limit() - buffer.position();
        byte[] data = new byte[length + 10];
        ByteBuffer buf = ByteBuffer.wrap(data);
        buf.put((byte) 'm');
        buf.putLong(cid);
        buf.put(MessageHeadData);
        buf.put(buffer);
        buf.flip();
        Coversocks.writeMessage(data);
    }

    Connector connector;
    private Thread connectorProcessor;
    private Runnable connectorRunner = new Runnable() {
        @Override
        public void run() {
            try {
                connector = new Connector(handler, 256 * 1024);
                connector.run();
            } catch (Exception e) {
                log.log(Level.WARNING, "message reader runner is stop by ", e);
            }
        }
    };
    Handler handler = new Handler() {
        @Override
        public void channelOpened(SelectableChannel channel) {
            if (channel instanceof SocketChannel) {
                SocketChannel socket = (SocketChannel) channel;
                protect(socket.socket());
            } else if (channel instanceof DatagramChannel) {
                DatagramChannel datagram = (DatagramChannel) channel;
                protect(datagram.socket());
            }
        }

        @Override
        public void channelFail(SelectionKey key, Exception e) {
            long cid = (long) key.attachment();
            messageWriteBack(cid, e.getMessage().getBytes());
        }

        @Override
        public void channelConnected(SelectionKey key) {
            long cid = (long) key.attachment();
            messageWriteBack(cid, null);
        }

        @Override
        public void channelClosed(SelectionKey key, Exception e) {
            long cid = (long) key.attachment();
            messageWriteClose(cid);
        }

        @Override
        public void receiveData(SelectionKey key, ByteBuffer buffer) {
            long cid = (long) key.attachment();
            messageWriteData(cid, buffer);
        }
    };


    protected File checkConfig(String filename) throws IOException {
        File file;
        InputStream in = null;
        OutputStream out = null;
        try {
            file = new File(this.getFilesDir(), filename);
            if (!file.exists()) {
                in = getAssets().open(filename);
                out = new FileOutputStream(file);
                byte[] buffer = new byte[1024];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                }
            }
        } catch (IOException e) {
            throw e;
        } finally {
            if (in != null) {
                in.close();
            }
            if (out != null) {
                out.close();
            }
        }
        return file;
    }

    private Thread messageReader;
    private boolean messageRunning;
    private Runnable messageReaderRunner = new Runnable() {
        @Override
        public void run() {
            try {
                messageRunning = true;
                while (messageRunning) {
                    procMessageRead();
                }
            } catch (Exception e) {
                log.log(Level.WARNING, "message reader runner is stop by ", e);
            }
        }
    };


//    private Thread netifRunner;
//    private Runnable procNetProc = new Runnable() {
//        @Override
//        public void run() {
//            try {
//                procNet();
//            } catch (Exception e) {
//                log.log(Level.WARNING, "net reader runner is stop by ", e);
//            }
//        }
//    };


    private byte[] messageBuffer = new byte[MTU + 100];

    private void procMessageRead() throws IOException {
        long n = Coversocks.readMessage(messageBuffer);
        if (n < 1) {
            throw new IOException("read return code " + n);
        }
        ByteBuffer buf = ByteBuffer.wrap(messageBuffer, 1, 8);
        long cid = buf.asLongBuffer().get();
        byte cmd = messageBuffer[9];
        switch (cmd) {
            case MessageHeadConn:
                try {
                    String url = new String(Arrays.copyOfRange(messageBuffer, 10, (int) n), "UTF-8");
                    connector.connect(cid, url);
                } catch (Exception e) {
                    log.log(Level.WARNING, "process message conn command on " + cid + " fail with ", e);
                }
                break;
            case MessageHeadData:
                try {
                    connector.write(cid, ByteBuffer.wrap(messageBuffer, 10, (int) n - 10));
                } catch (Exception e) {
                    log.log(Level.WARNING, "process message data command on " + cid + " fail with ", e);
                }
                break;
            case MessageHeadClose:
                try {
                    connector.close(cid);
                } catch (Exception e) {
                    log.log(Level.WARNING, "process message close command on " + cid + " fail with ", e);
                }
                break;
        }

    }


    private void procNet() throws IOException, InterruptedException {
        FileOutputStream out = new FileOutputStream(mInterface.getFileDescriptor());
        FileInputStream in = new FileInputStream(mInterface.getFileDescriptor());
        byte[] buffer = new byte[MTU];
        int n;
        boolean readed;
        while (true) {
            readed = false;
            n = (int) Coversocks.readQuque(buffer, 0, MTU);
            if (n > 0) {
                out.write(buffer, 0, n);
                readed = true;
            }
            n = in.read(buffer);
            if (n > 0) {
                Coversocks.writeQuque(buffer, 0, n);
                readed = true;
            }
            if (!readed) {
                Thread.sleep(10);
            }
        }
    }

    private void bootstrap() {
        try {
            this.checkConfig("gfwlist.txt");
            this.checkConfig("user_rules.txt");
            File conf = this.checkConfig("default-client.json");
            mInterface = builder.setSession("Coversocks")
                    .setMtu(MTU)
                    .addAddress("172.23.0.1", 24)
                    .addDnsServer("192.168.1.1")
                    .addRoute("0.0.0.0", 0).establish();
            File dump = new File(this.getFilesDir(), "dump.pcap");
            String res = Coversocks.bootstrapFD(conf.getAbsolutePath(), MTU, (long) mInterface.getFd(), dump.getAbsolutePath());
//            String res = Coversocks.bootstrapQuque(conf.getAbsolutePath(), MTU, true, true);
            if (res == null || res.length() > 0) {
                mInterface.close();
                log.log(Level.WARNING, "Coversocks bootstrap fail with " + res);
                return;
            }
            Coversocks.start();
            Coversocks.proxySet("182.168.1.1", false);
//            this.netifRunner = new Thread(this.procNetProc);
//            this.netifRunner.start();
            this.messageReader = new Thread(this.messageReaderRunner);
            this.messageReader.start();
            this.connectorProcessor = new Thread(this.connectorRunner);
            this.connectorProcessor.start();
        } catch (Exception e) {
            log.log(Level.WARNING, "vpn service bootstrap fail with ", e);
            return;
        }
    }
}
