package cn.ac.iscas.sensorcollector;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.TextView;

import java.net.Socket;

public class MainActivity extends AppCompatActivity {

    private final String TAG = "MainActivity";

    // ui views
    private TextView mConnectionStatus = null;
    private TextView mRemoteIp = null;
    private TextView mReceiveToSendPort = null;
    private TextView mSendToReceivePort = null;

    // remote info
    private int mRemoteReceivePort = -1;
    private int mRemoteSendPort = -1;

    private SensorCollectorService mService = null;
    private ServiceConnection mConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Util.logd(TAG, "service connected!");
            mService = ((SensorCollectorService.ServiceBinder)service).getService();

            mService.setActivityHandler(new MyHandler());
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Util.logd(TAG, "service disconnected!");
            mService = null;
        }
    };

    final class MyHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);

            Util.logd(TAG, "msg: " + msg.what);
            switch (msg.what) {
                case SensorCollectorService.MSG_RECEIVE_CONNECTED :
                    mConnectionStatus.setText(R.string.connection_status_connected);
                    mRemoteIp.setText(((Socket)msg.obj).getRemoteSocketAddress().toString());
                    mRemoteSendPort = ((Socket)msg.obj).getPort();
                    mReceiveToSendPort.setText(Constants.ANDROID_RECEIVE_PORT + " --- " + mRemoteSendPort);
                    break;
                case SensorCollectorService.MSG_RECEIVE_DISCONNECTED:
                    mConnectionStatus.setText(R.string.connection_status_disconnected);
                    mRemoteIp.setText(R.string.no_value_to_show);
                    mReceiveToSendPort.setText(R.string.no_value_to_show);
                    mSendToReceivePort.setText(R.string.no_value_to_show);
                    break;
                case SensorCollectorService.MSG_SEND_CONNECTED:
                    mRemoteReceivePort = ((Socket)msg.obj).getPort();
                    mSendToReceivePort.setText(Constants.ANDROID_SEND_PORT + " --- " + mRemoteReceivePort);
                    break;
                case SensorCollectorService.MSG_SEND_DISCONNECTED:
                    break;
                default:
                    Util.logd(TAG, "unknown message!!!");
                    break;

            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mConnectionStatus = (TextView) this.findViewById(R.id.conn_status);
        mRemoteIp = (TextView) this.findViewById(R.id.remote_ip);
        mReceiveToSendPort = (TextView) this.findViewById(R.id.rcv_snd_port);
        mSendToReceivePort = (TextView) this.findViewById(R.id.snd_rcv_port);

        Intent intent = new Intent(this, SensorCollectorService.class);
        startService(intent);
        bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        unbindService(mConnection);
    }
}
