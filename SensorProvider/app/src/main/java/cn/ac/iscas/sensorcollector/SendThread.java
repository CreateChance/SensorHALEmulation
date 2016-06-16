package cn.ac.iscas.sensorcollector;

import android.os.Handler;

import java.io.IOException;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * Created by baniel on 5/10/16.
 */
public class SendThread extends Thread {

    private final String TAG = "SendThread";

    Handler mServerHandler = null;

    ServerSocket mSendSocket = null;
    Socket mSocket = null;
    OutputStream os = null;

    // response
    private SenserServiceProto.Response mResponse = null;

    // whether running
    private boolean mRunning = true;
    private boolean mClientConnected = false;

    public SendThread(Handler handler) {
        this.mServerHandler = handler;
    }

    @Override
    public void run() {
        super.run();

        Util.logd(TAG, "send thread start!!");

        while (mRunning) {
            try {
                Util.logd(TAG, "Waiting for connect......");
                if (mSendSocket == null) {
                    mSendSocket = new ServerSocket(Constants.ANDROID_SEND_PORT);
                    mSendSocket.setReuseAddress(true);
                }
                mSocket = mSendSocket.accept();
                os = mSocket.getOutputStream();
                mClientConnected = true;

                // Tell service send connected.
                mServerHandler.obtainMessage(SensorCollectorService.MSG_SEND_CONNECTED, mSocket).sendToTarget();
                Util.logd(TAG, "send client connected!!");
                while (mClientConnected) {
                    if (mResponse == null) {
                        try {
                            synchronized (this) {
                                this.wait();
                            }
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                            Util.loge(TAG, "we are interrupted, so stop running.");
                            mClientConnected = false;
                        }
                    }

                    if (mResponse != null) {
                        //Util.logd(TAG, "sending response.");
                        mResponse.writeDelimitedTo(os);
                        mResponse = null;
                        //mServerHandler.obtainMessage(SensorCollectorService.MSG_SEND_RESPONSE).sendToTarget();
                    }
                }
            } catch (IOException e) {
                Util.loge(TAG, "network error, now quit send thread.");
                mClientConnected = false;
                mResponse = null;
                mServerHandler.obtainMessage(SensorCollectorService.MSG_SEND_DISCONNECTED).sendToTarget();
                e.printStackTrace();
            }
        }
    }

    public synchronized boolean sendResponse(SenserServiceProto.Response response) {

        if (response == null) {
            Util.loge(TAG, "response should not be null!");
            return false;
        }

        //TODO: here we may drop some package, so we should hold MQ here.
        if (mResponse != null) {
            //Util.logd(TAG, "Skip data!");
            //return  false;
        }

        this.mResponse = response;

        this.notify();

        return true;
    }

    // receive socket has disconnected, so we disconnect too.
    public void notifyToDisconnect() {
        if (mResponse != null) {
            try {
                Util.logd(TAG, "notify to closing all the io resource.");
                os.close();
                mSocket.close();
                return;
            } catch (IOException e) {
                e.printStackTrace();
            }

        }

        if (mClientConnected) {

            Util.logd(TAG, "notify to interrupt!!");
            this.interrupt();
        }
    }
}
