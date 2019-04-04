/*
 * Tencent is pleased to support the open source community by making wechat-matrix available.
 * Copyright (C) 2018 THL A29 Limited, a Tencent company. All rights reserved.
 * Licensed under the BSD 3-Clause License (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://opensource.org/licenses/BSD-3-Clause
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package sample.tencent.matrix.fd;

import android.Manifest;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Looper;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.system.Os;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import com.tencent.matrix.Matrix;
import com.tencent.matrix.fdcanary.FDCanaryPlugin;
import com.tencent.matrix.fdcanary.core.FDCanaryJniBridge;
import com.tencent.matrix.fdcanary.core.FDDumpInfo;
import com.tencent.matrix.plugin.Plugin;
import com.tencent.matrix.util.MatrixLog;
import com.tencent.matrix.util.MatrixUtil;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

import sample.tencent.matrix.R;
import sample.tencent.matrix.issue.IssueFilter;


public class TestFdActivity extends Activity {
    private static final String TAG = "Matrix.TestFDActivity";
    private static final int EXTERNAL_STORAGE_REQ_CODE = 0x1;

    private List<AlertDialog> dialogs = new ArrayList<>();
    private List<Looper> loopers = new ArrayList<>();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.test_fd);
        IssueFilter.setCurrentFilter(IssueFilter.ISSUE_FD);
        requestPer();

        Plugin plugin = Matrix.with().getPluginByClass(FDCanaryPlugin.class);
        if (!plugin.isPluginStarted()) {
            MatrixLog.i(TAG, "plugin-fd start");
            plugin.start();
        }

    }

    private void requestPer() {
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {

            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                Toast.makeText(this, "please give me the permission", Toast.LENGTH_SHORT).show();
            } else {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                        EXTERNAL_STORAGE_REQ_CODE);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode) {
            case EXTERNAL_STORAGE_REQ_CODE:
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                }
                break;
        }
    }

    public void onClick(View v) {
        if (v.getId() == R.id.stream) {
            writeSth();
        } else if (v.getId() == R.id.input_channel) {
            testInputChannel();
        } else if (v.getId() == R.id.thread) {
            testThread();
        } else if (v.getId() == R.id.looper) {
            testLooper();
        } else if (v.getId() == R.id.exit_looper) {
            exitLooper();
        } else if (v.getId() == R.id.cursor) {
            testCursor();
        } else if (v.getId() == R.id.socket) {
            testSocket();
        } else if (v.getId() == R.id.pipe) {

        } else if (v.getId() == R.id.dump_single) {
            FDCanaryJniBridge.dumpFDInfo(FDDumpInfo.FDDumpStrategyConstants.MANUAL, android.os.Process.myPid(), MatrixUtil.getProcessName(TestFdActivity.this));
        }
    }

    public void testInputChannel() {
        for (int i = 0; i < 1; i++) {
            //recount();
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            // 设置参数
            builder.setTitle("TEST")
                    .setMessage("TEST")
                    .setPositiveButton("CLOSE" + i, new DialogInterface.OnClickListener() {// 积极

                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            for (AlertDialog a_dialog : dialogs) {
                                a_dialog.cancel();
                            }
                        }
                    }).setNegativeButton("DUMP", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {

                }
            });
            AlertDialog dialog = builder.create();
            dialog.show();
            dialogs.add(dialog);
        }
    }

    private void testThread() {

        for (int i = 0; i < 10; i ++) {
            Thread thread = new Thread(new Runnable() {
                @Override
                public void run() {
                    Log.d(TAG, "testThread id: " + Thread.currentThread());
                    try {
                        Thread.sleep(30000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            });

            thread.start();
        }
    }

    private void testLooper() {

        for (int i = 0; i < 10; i ++) {
            Thread thread = new Thread(new Runnable() {
                @Override
                public void run() {


                    Looper.prepare();
                    Looper looper = Looper.myLooper();
                    loopers.add(looper);
                    Log.d(TAG, "testLooper id: " + Thread.currentThread() + " Loopers size is " + loopers.size());

                    Looper.loop();
                }
            });

            thread.start();
        }
    }

    private void exitLooper() {
        Log.d(TAG, "exitLooper size is" + loopers.size());
        for (Looper looper : loopers) {
            looper.quitSafely();
            Log.d(TAG, "exitLooper");
        }
    }

    private void testCursor() {
        for (int i = 0; i < 10; i ++) {
            Uri uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
            ContentResolver cr = this.getContentResolver();//mContext是一个Context对象
            Cursor cs = cr.query(uri, null, null, null, null);
        }
    }

    private void testSocket() {
        for (int i = 0; i < 10; i ++) {
            Thread thread = new Thread() {
                @Override
                public void run() {
                    super.run();
                    Socket socket;
                    try {
                        socket = new Socket("192.168.1.1", 1989);
                        InputStream inputStream = new FileInputStream("e://a.txt");
                        OutputStream outputStream = socket.getOutputStream();
                        byte buffer[] = new byte[4 * 1024];
                        int temp = 0;
                        while ((temp = inputStream.read(buffer)) != -1) {
                            outputStream.write(buffer, 0, temp);
                        }
                        outputStream.flush();

                    } catch (UnknownHostException e) {
                        e.printStackTrace();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            };

            thread.start();

        }

    }

    private void writeSth() {
        for (int j = 0; j < 10; j ++) {
            try {
                File f = new File("/sdcard/a.txt");
                if (f.exists()) {
                    f.delete();
                }
                byte[] data = new byte[4096];
                for (int i = 0; i < data.length; i++) {
                    data[i] = 'a';
                }
                FileOutputStream fos = new FileOutputStream(f);
                for (int i = 0; i < 10; i++) {
                    fos.write(data);
                }

                fos.flush();
                fos.close();
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
