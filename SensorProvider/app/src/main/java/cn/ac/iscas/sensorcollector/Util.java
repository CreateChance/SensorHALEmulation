package cn.ac.iscas.sensorcollector;

import android.util.Log;

/**
 * Created by baniel on 5/5/16.
 */
public class Util {

    private static final boolean DEBUG = true;

    public static void logd(String tag, String msg) {
        if (DEBUG) {
            Log.d(tag, msg);
        }
    }

    public static void loge(String tag, String msg) {
        Log.e(tag, msg);
    }
}
