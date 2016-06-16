package cn.ac.iscas.sensorcollector;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.TriggerEvent;
import android.hardware.TriggerEventListener;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;

import java.io.BufferedReader;
import java.util.List;

public class SensorCollectorService extends Service {

    private final String TAG = "SensorCollectorService";

    // activity handler, hold this to update ui.
    private Handler mActivityHandler = null;

    // sensor manager
    private SensorManager mSensorManager = null;
    // sensor list
    private List<Sensor> mSensorList = null;
    private SenserServiceProto.Response mSensorListResponse = null;

    // messages
    public static final int MSG_RECEIVE_REQUEST = 100;
    public static final int MSG_SEND_RESPONSE = 101;
    public static final int MSG_RECEIVE_CONNECTED = 102;
    public static final int MSG_RECEIVE_DISCONNECTED = 103;
    public static final int MSG_SEND_CONNECTED = 104;
    public static final int MSG_SEND_DISCONNECTED = 105;

    private MyHandler mHandler = new MyHandler();

    // working threads
    private ReceiveThread mReceiveThread = null;
    private SendThread mSendThread = null;

    // listeners
    private MySensorEventListener mSensorEventListener = new MySensorEventListener();
    private MySensorTriggerListener mSensorTriggerListener = new MySensorTriggerListener();

    public SensorCollectorService() {
    }

    public class ServiceBinder extends Binder {
        SensorCollectorService getService() {
            return SensorCollectorService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();

        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        // FIXME: we should check mSensorManager is null here.
        if (mSensorManager != null) {
            mSensorList = mSensorManager.getSensorList(Sensor.TYPE_ALL);
            initSensorListResponse();
        }

        mReceiveThread = new ReceiveThread(mHandler);
        mSendThread = new SendThread(mHandler);

        //start working threads
        mReceiveThread.start();
        mSendThread.start();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return new ServiceBinder();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Util.logd(TAG, "onStartCommand");


        return START_NOT_STICKY;
    }

    public void setActivityHandler(Handler handler) {
        this.mActivityHandler = handler;
    }

    private void initSensorListResponse() {
        SenserServiceProto.Response.Builder ResBuilder =
                SenserServiceProto.Response.newBuilder().
                        setRspType(SenserServiceProto.Response.ResponseType.SENSOR_LIST);
        SenserServiceProto.Sensor.Builder sensorBuilder;

        int flags = 0;

        for (int i = 0; i < mSensorList.size(); i++) {
            Sensor tmp = mSensorList.get(i);
            sensorBuilder = SenserServiceProto.Sensor.newBuilder().setName(tmp.getName())
                    .setVendor(tmp.getVendor())
                    .setVersion(tmp.getVersion())
                    .setHandle(i)
                    .setType(tmp.getType())
                    .setMaxRange(tmp.getMaximumRange())
                    .setResolution(tmp.getResolution())
                    .setPower(tmp.getPower())
                    .setMinDelay(tmp.getMinDelay())
                    .setFifoReservedEventCount(tmp.getFifoReservedEventCount())
                    .setFifoMaxEventCount(tmp.getFifoMaxEventCount())
                    .setStringType(tmp.getStringType())
                    .setMaxDelay(tmp.getMaxDelay());

            if (tmp.isWakeUpSensor()) {
                flags |= 0x01;
            }
            sensorBuilder.setFlags(flags | tmp.getReportingMode());

            if (tmp.getType() == Sensor.TYPE_HEART_RATE) {
                sensorBuilder.setRequiredPermission("android.permission.BODY_SENSORS");
            } else {
                sensorBuilder.setRequiredPermission("");
            }

            ResBuilder.addSensors(sensorBuilder.build());

            flags = 0;
        }

        mSensorListResponse = ResBuilder.build();
    }

    private class MySensorEventListener implements SensorEventListener {
        @Override
        public void onSensorChanged(SensorEvent event) {
            SenserServiceProto.SensorDataEvent.Builder builder = SenserServiceProto.SensorDataEvent.newBuilder()
                    .setAccuracy(event.accuracy)
                    .setSensorHandle(mSensorList.indexOf(event.sensor))
                    .setTimestamp(event.timestamp)
                    .setXvalue(event.values[0])
                    .setYvalue(event.values[1])
                    .setZvalue(event.values[2]);
            SenserServiceProto.Response response = SenserServiceProto.Response.newBuilder()
                    .setRspType(SenserServiceProto.Response.ResponseType.SENSOR_DATA)
                    .setSensorData(builder.build()).build();

            // send to remote.
            mSendThread.sendResponse(response);
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
            SenserServiceProto.SensorAccuracyEvent accuracyEvent = SenserServiceProto.SensorAccuracyEvent.newBuilder()
                    .setSensorHandle(mSensorList.indexOf(sensor))
                    .setAccuracy(accuracy).build();
            SenserServiceProto.Response response = SenserServiceProto.Response.newBuilder()
                    .setRspType(SenserServiceProto.Response.ResponseType.SENSOR_ACCURACY)
                    .setSensorAccuracy(accuracyEvent).build();

            // send to remote.
            mSendThread.sendResponse(response);
        }
    }

    private class MySensorTriggerListener extends TriggerEventListener {
        @Override
        public void onTrigger(TriggerEvent event) {
            SenserServiceProto.SensorDataEvent.Builder builder = SenserServiceProto.SensorDataEvent.newBuilder()
                    .setSensorHandle(mSensorList.indexOf(event.sensor))
                    .setTimestamp(event.timestamp)
                    .setXvalue(event.values[0])
                    .setYvalue(event.values[1])
                    .setZvalue(event.values[2]);
            SenserServiceProto.Response response = SenserServiceProto.Response.newBuilder()
                    .setRspType(SenserServiceProto.Response.ResponseType.SENSOR_DATA)
                    .setSensorData(builder.build()).build();

            // send to remote.
            mSendThread.sendResponse(response);
        }
    }

    private class MyHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);

            Util.logd(TAG, "msg: " + msg.what);
            switch (msg.what) {
                case MSG_RECEIVE_REQUEST:
                    handleRequest((SenserServiceProto.Request) msg.obj);
                    break;
                case MSG_SEND_RESPONSE:

                    break;
                case MSG_RECEIVE_CONNECTED:
                    // tell ui to show current state.
                    mActivityHandler.handleMessage(msg);

                    // do ourselves things here.
                    break;
                case MSG_RECEIVE_DISCONNECTED:
                    // tell ui to show current state.
                    mActivityHandler.handleMessage(msg);
                    mSensorManager.unregisterListener(mSensorEventListener);
                    mSendThread.notifyToDisconnect();
                    // do ourselves things here.
                    break;
                case MSG_SEND_CONNECTED:
                    // tell ui to show current state.
                    mActivityHandler.handleMessage(msg);

                    // do ourselves things here.
                    break;
                case MSG_SEND_DISCONNECTED:
                    // tell ui to show current state.
                    mActivityHandler.handleMessage(msg);
                    mSensorManager.unregisterListener(mSensorEventListener);
                    mReceiveThread.notifyToDisconnect();
                    // do ourselves things here.
                    break;
                default:
                    Util.loge(TAG, "unknown message!");
                    break;
            }
        }
    }

    private void handleRequest(SenserServiceProto.Request request) {

        if (request == null) {
            return;
        }

        switch (request.getReqType().getNumber()) {
            case SenserServiceProto.Request.RequestType.REGISTER_VALUE:
                registerListener(request.getRegListener());
                break;
            case SenserServiceProto.Request.RequestType.UNREGISTER_VALUE:
                unregisterListener(request.getUnRegListener());
                break;
            case SenserServiceProto.Request.RequestType.GET_SENSOR_LIST_VALUE:
                mSendThread.sendResponse(mSensorListResponse);
                break;
        }
    }

    private void registerListener(SenserServiceProto.RegisterListener registerListener) {
        Util.logd(TAG, "register handle: " + registerListener.getSensorHandle());
        if (registerListener.getType() == SenserServiceProto.RegisterListener.RegisterType.STREAM) {
            mSensorManager.registerListener(mSensorEventListener, mSensorList.get(registerListener.getSensorHandle()),
                    registerListener.getSamplingPeriodUs(), registerListener.getMaxReportLatencyUs());
        } else {
            mSensorManager.requestTriggerSensor(mSensorTriggerListener, mSensorList.get(registerListener.getSensorHandle()));
        }
    }

    private void unregisterListener(SenserServiceProto.UnregisterListener unregisterListener) {
        mSensorManager.unregisterListener(mSensorEventListener, mSensorList.get(unregisterListener.getSensorHandle()));
    }
}
