package com.example.p2ptext;

import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;

public class Logger {
    private static final String fileName = "P2pConnect_log";
    private static final String directoryName = "PMS/";
    private static final String LOGGER_TAG = "Logger";
    private String phoneNumber;


    public Logger(String phoneNumber) {
        this.phoneNumber = phoneNumber;
        Log.v(LOGGER_TAG, "Logging Initiated");
    }

    public void addMessageToLog(String message) {
        File dir = Environment.getExternalStoragePublicDirectory(directoryName);
        DateFormat df = new SimpleDateFormat("yyyyMMddHHmmss");
        String date = df.format(Calendar.getInstance().getTime());
        File logFile = new File(dir, fileName + "_" + phoneNumber + ".txt");
        if (!logFile.exists()) {
            try {
                Log.d(LOGGER_TAG, "File created ");
                logFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        try {
            FileWriter buf = new FileWriter(logFile, true);
            buf.write(date + ',' + message + "\r\n");
            buf.flush();
            buf.close();
        } catch (Exception ignored) {
        }
    }

}
