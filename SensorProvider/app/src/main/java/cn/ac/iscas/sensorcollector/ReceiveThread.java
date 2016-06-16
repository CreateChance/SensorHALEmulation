package cn.ac.iscas.sensorcollector;

import android.os.Handler;

import com.google.protobuf.CodedInputStream;
import com.google.protobuf.ExtensionRegistryLite;
import com.google.protobuf.InvalidProtocolBufferException;

import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * Created by baniel on 5/10/16.
 */
public class ReceiveThread extends Thread {

    private final String TAG = "ReceiveThread";

    // server socket and streams
    ServerSocket mReceiveSocket = null;
    Socket mSocket = null;
    InputStream is = null;

    // Request message
    SenserServiceProto.Request mRequest = null;

    private Handler mServerHander = null;

    // whether this thread is running
    private boolean mRunning = true;
    private boolean mClientConnected = false;

    public ReceiveThread(Handler handler) {
        this.mServerHander = handler;
    }

    @Override
    public void run() {
        super.run();

        Util.logd(TAG, "receive thread start!!");

        while (mRunning) {
            try {
                Util.logd(TAG, "Waiting for connect......");
                if (mReceiveSocket == null) {
                    mReceiveSocket = new ServerSocket(Constants.ANDROID_RECEIVE_PORT);
                    mReceiveSocket.setReuseAddress(true);
                }
                mSocket = mReceiveSocket.accept();
                is = mSocket.getInputStream();

                mClientConnected = true;
                // Tell service client is connected to us.
                mServerHander.
                        obtainMessage(SensorCollectorService.MSG_RECEIVE_CONNECTED, mSocket).sendToTarget();
                Util.logd(TAG, "receive client connected!!");
                while (mClientConnected) {
                    try {
                        // FIXME: maybe we should hold a message queue here??
                        Util.logd(TAG, "reading request......");
                        mRequest = SenserServiceProto.Request.parseDelimitedFrom(is);
                    } catch (InvalidProtocolBufferException e) {
                        Util.logd(TAG, "parse protobuf error! Just skip this request.");
                        e.printStackTrace();
                        continue;
                    }
                    if (mRequest == null) {
                        Util.logd(TAG, "request is null, maybe remote client is dead amd we disconnect it.");
                        mClientConnected = false;
                        is.close();
                        mSocket.close();
                        mServerHander.
                                obtainMessage(SensorCollectorService.MSG_RECEIVE_DISCONNECTED).sendToTarget();
                    } else {
                        Util.logd(TAG, "sending receive request msg.");
                        mServerHander.
                                obtainMessage(SensorCollectorService.MSG_RECEIVE_REQUEST, mRequest).sendToTarget();
                    }
                }

                Util.logd(TAG, "client disconnected, continue to listening.");
            } catch (IOException e) {
                Util.loge(TAG, "Network error! Now quit receiver thread.");
                mServerHander.
                        obtainMessage(SensorCollectorService.MSG_RECEIVE_DISCONNECTED).sendToTarget();
                mClientConnected = false;
                mRequest = null;
                e.printStackTrace();
            }
        }
    }

    // send socket has disconnected , so we disconnected too.
    public void notifyToDisconnect() {
        if (mClientConnected) {
            try {
                is.close();
                mSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
