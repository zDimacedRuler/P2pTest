package com.example.p2ptext;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import java.io.File;

public class SplashActivity extends AppCompatActivity {
    private static final int PERMISSION_ALL = 1;
    public File pmsFolder = Environment.getExternalStoragePublicDirectory("PMS/");
    public File workingFolder = Environment.getExternalStoragePublicDirectory("PMS/Working");
    String[] PERMISSIONS = {
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.RECORD_AUDIO};
    private EditText phoneText;
    private Button submitButton;
    private SharedPreferences sp;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        sp = PreferenceManager.getDefaultSharedPreferences(this);
        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!checkPermissions(this, PERMISSIONS)) {
                Log.i("permission", "request permissions");
                ActivityCompat.requestPermissions(this, PERMISSIONS, PERMISSION_ALL);
            }
        }
        creatingFolders();
        if (sp.getString("phone_no", null) != null) {
            Intent i = new Intent(this, MainActivity.class);
            startActivity(i);
            finish();
        } else {
            setContentView(R.layout.activity_splash);
            submitButton = findViewById(R.id.submit_button);
            phoneText = findViewById(R.id.phone_text_edit);
            submitButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    onSubmitNumber(v);
                }
            });
        }
    }

    public void onSubmitNumber(View view) {
        final String phoneTextVal = phoneText.getText().toString();

        if (phoneTextVal.length() == 10 && phoneTextVal.matches("^[789]\\d{9}$")) {
            SharedPreferences.Editor editor = sp.edit();
            editor.putString("phone_no", phoneTextVal);
            editor.apply();
            Intent i = new Intent(this, MainActivity.class);
            startActivity(i);
            finish();
        } else {
            phoneText.setError("Enter Valid Number");
        }
    }

    public void creatingFolders() {
        if (!pmsFolder.exists()) {
            pmsFolder.mkdir();
        }
        if (!workingFolder.exists()) {
            workingFolder.mkdir();
        }
    }


    private boolean checkPermissions(Context context, String[] permissions) {
        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && context != null && permissions != null) {
            for (String permission : permissions) {
                if (ActivityCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
                    return false;
                }
            }
        }
        return true;
    }

    public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[], @NonNull int[] grantResults) {
        switch (requestCode) {
            case PERMISSION_ALL: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    creatingFolders();

                } else {
                    Toast.makeText(this, "Permission not granted.Exiting!!", Toast.LENGTH_SHORT).show();
                    finish();
                }
            }
            // other 'case' lines to check for other
            // permissions this app might request
        }
    }

}
