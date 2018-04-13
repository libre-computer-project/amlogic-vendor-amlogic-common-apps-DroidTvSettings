package com.droidlogic.tv.settings.display.dolbyvision;

import java.util.ArrayList;
import java.util.List;
import java.util.TimerTask;
import java.util.Timer;
import java.util.TimerTask;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import android.app.ActivityManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.WindowManager;
import android.widget.Button;
import android.view.Gravity;
import android.util.Log;

import com.droidlogic.tv.settings.R;


import android.widget.Toast;

public class DolbyVisionService extends Service
{
    private static String TAG = "DvService";
    public static final String OPERATION = "operation";
    public static final int OPERATION_SHOW = 100;
    public static final int OPERATION_HIDE = 101;

    private static WindowManager wm;
    private static WindowManager.LayoutParams params;
    private Button btn_floatView;
    private boolean isViewAdded = false;

    private final int FADE_TIME_5S = 5000;
    private final int FADE_TIME_2S = 2000;

    private static final int MSG_DV_VIEW_OUT = 0xd1;
    private static final int MSG_DV_VIEW_CREAT = 0xd2;
    private static final int MSG_SCAN_FILE = 0xd4;
    private final String VFM_MAP_PATH = "/sys/class/vfm/map";
    private final String TV_DV_STRGING = "dvbldec(1) amlvideo(1)";
    private final String HDMI_DV_STRGING = "dv_vdin(1)";
    private String txtContext = null;
    private String updateTxtContext = null;
    private Timer cancelTimer = new Timer();

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        Log.d(TAG, "[onCreate]");
        super.onCreate();
//        Toast.makeText(DolbyVisionService.this, "Dolby Vision Service Start!",Toast.LENGTH_LONG).show();
        txtContext = readVfmMap();
        scanFile();
    }

    public void scanFile() {
        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                Message msg = new Message();
                msg.what = MSG_SCAN_FILE;
                mHandler.sendMessage(msg);
            }
        }, 0, 500);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    @Override
    public void onStart(Intent intent, int startId) {
        Log.i(TAG,"[onStart]");
        super.onStart(intent, startId);
    }

    TimerTask task1 = new TimerTask() {
        public void run() {
            Message message = Message.obtain();
            message.what = MSG_DV_VIEW_OUT;
            mHandler.sendMessage (message);
        }
    };

    private void cancelFloatView() {
        TimerTask task1 = new TimerTask() {
            public void run() {
                Message message = Message.obtain();
                message.what = MSG_DV_VIEW_OUT;
                mHandler.sendMessage (message);
            }
        };
        cancelTimer.cancel();
        cancelTimer = new Timer();
        cancelTimer.schedule (task1, 5000);
    }

    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
            case MSG_DV_VIEW_OUT:
                removeView();
                break;
            case MSG_SCAN_FILE:
//                Log.i(TAG, "[mHandler]isSwitchVideo:"+isSwitchVideo()+",isDv:"+isDv());
                if (isSwitchVideo()) {
//                    Log.i(TAG, "[mHandler]txtContext:"+txtContext+",updateTxtContext:"+updateTxtContext);
                    txtContext = updateTxtContext;
                    if (isDv() && !isViewAdded) {
//                        Log.i(TAG, "[mHandler]txtContext:"+txtContext);
                        addView();
                        cancelFloatView();
                    }
                }
                break;
            }
        }
    };

    private void createFloatView() {
        btn_floatView = new Button(getApplicationContext());
        btn_floatView.getBackground().setAlpha(0);
        btn_floatView.setBackgroundResource(R.drawable.earth);

        wm = (WindowManager) getApplicationContext().getSystemService(
                Context.WINDOW_SERVICE);
        params = new WindowManager.LayoutParams();
        params.type = WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY;
        params.gravity = Gravity.LEFT | Gravity.TOP;
        params.format = PixelFormat.TRANSLUCENT;
        params.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        params.width = 230;
        params.height = 30;

        btn_floatView.setOnTouchListener(new OnTouchListener() {
            int lastX, lastY;
            int paramX, paramY;

            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    lastX = (int) event.getRawX();
                    lastY = (int) event.getRawY();
                    paramX = params.x;
                    paramY = params.y;
                    break;
                case MotionEvent.ACTION_MOVE:
                    int dx = (int) event.getRawX() - lastX;
                    int dy = (int) event.getRawY() - lastY;
                    params.x = paramX + dx;
                    params.y = paramY + dy;
                    wm.updateViewLayout(btn_floatView, params);
                    break;
                }
                return true;
             }
        });
    }

    private void addView() {
        if (!isViewAdded) {
            createFloatView();
            wm.addView(btn_floatView, params);
            isViewAdded = true;
        }
    }

    private void removeView() {
        if (isViewAdded) {
            wm.removeView(btn_floatView);
            isViewAdded = false;
        }
    }


    public boolean isSwitchVideo() {
        updateTxtContext = readVfmMap();
        if (updateTxtContext != null && updateTxtContext.equals(txtContext)) {
            return false;
        }
        else
            return true;
    }

    public  String readVfmMap() {
        File file = null;
        file = new File(VFM_MAP_PATH);
        FileInputStream fileInputStream = null;
        try {
            fileInputStream = new FileInputStream(file);
        } catch (FileNotFoundException e1) {
            e1.printStackTrace();
            Log.e(TAG, "readVfmMap fileInputStream erro: " + e1.getMessage());
            return null;
        }
        StringBuffer sb = new StringBuffer();
        InputStreamReader inputStreamReader = null;
        inputStreamReader = new InputStreamReader(fileInputStream);
        BufferedReader reader = new BufferedReader(inputStreamReader);
        String line;
        try {
            while ((line = reader.readLine()) != null) {
                sb.append(line+" ");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return sb.toString();
    }

    public boolean isDv() {
        updateTxtContext = readVfmMap();
        if (updateTxtContext != null) {
            if (updateTxtContext.contains(TV_DV_STRGING) || updateTxtContext.contains(HDMI_DV_STRGING))
                return true;
            else
                return false;
        }
        return false;
    }

}
