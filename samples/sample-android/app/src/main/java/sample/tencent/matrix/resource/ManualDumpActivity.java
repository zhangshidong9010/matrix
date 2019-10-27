package sample.tencent.matrix.resource;

import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import com.tencent.matrix.Matrix;
import com.tencent.matrix.resource.ResourcePlugin;
import com.tencent.matrix.resource.analyzer.ActivityLeakAnalyzer;
import com.tencent.matrix.resource.analyzer.model.ActivityLeakResult;
import com.tencent.matrix.resource.analyzer.model.AndroidExcludedRefs;
import com.tencent.matrix.resource.analyzer.model.ExcludedRefs;
import com.tencent.matrix.resource.analyzer.model.HeapSnapshot;
import com.tencent.matrix.resource.config.SharePluginInfo;
import com.tencent.matrix.resource.hproflib.HprofBufferShrinker;
import com.tencent.matrix.resource.watcher.ActivityRefWatcher;
import com.tencent.matrix.resource.watcher.AndroidHeapDumper;
import com.tencent.matrix.resource.watcher.DumpStorageManager;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;

import sample.tencent.matrix.R;

public class ManualDumpActivity extends AppCompatActivity {

    private static final String TAG = "ManualDumpActivity";
    private String refString = null;
    private String leakActivity = null;
    private TextView refTv;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.manual_dump_activity);
        refTv = findViewById(R.id.leak_activity_ref);
        leakActivity = getIntent().getStringExtra(SharePluginInfo.ISSUE_ACTIVITY_NAME);
        refString = getIntent().getStringExtra(SharePluginInfo.ISSUE_REF_KEY);
        TextView textViewCompat = findViewById(R.id.leak_activity_name);
        textViewCompat.setText(leakActivity);
    }

    public void onClick(View view) {
        ResourcePlugin plugin = Matrix.with().getPluginByClass(ResourcePlugin.class);
        if (null == plugin) {
            return;
        }
        ActivityRefWatcher watcher = plugin.getWatcher();
        if (null == watcher) {
            return;
        }
        final AndroidHeapDumper dumper = watcher.getHeapDumper();
        if (null == dumper) {
            return;
        }
        HandlerThread handlerThread = new HandlerThread("DumpHprofWorker");
        handlerThread.start();
        final Handler handler = new Handler(handlerThread.getLooper());
        view.animate().alpha(0f).setDuration(300).withEndAction(new Runnable() {
            @Override
            public void run() {
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        long start = System.currentTimeMillis();
                        File file = dumper.dumpHeap();
                        Log.i(TAG, String.format("cost=%sms refString=%s path=%s", System.currentTimeMillis() - start, refString, file.getAbsolutePath()));
                        final ActivityLeakResult result = analyze(file, refString);
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if (result.mLeakFound && result.referenceChain != null) {
                                    refTv.setTextColor(getResources().getColor(android.R.color.holo_orange_dark));
                                    refTv.setText(result.referenceChain.toString());
                                } else if (result.mFailure != null) {
                                    refTv.setTextColor(getResources().getColor(android.R.color.holo_red_dark));
                                    JSONObject jsonObject = new JSONObject();
                                    try {
                                        result.encodeToJSON(jsonObject);
                                    } catch (JSONException e) {
                                    }
                                    refTv.setText(jsonObject.toString());
                                } else {
                                    refTv.setTextColor(getResources().getColor(android.R.color.holo_green_dark));
                                    refTv.setText("not found!");
                                }
                            }
                        });
                    }
                });
            }
        }).start();


    }

    private static String getShrinkHprofName(File origHprof) {
        final String origHprofName = origHprof.getName();
        final int extPos = origHprofName.indexOf(DumpStorageManager.HPROF_EXT);
        final String namePrefix = origHprofName.substring(0, extPos);
        return namePrefix + "_shrink" + DumpStorageManager.HPROF_EXT;
    }

    private ActivityLeakResult analyze(File hprofFile, String referenceKey) {
        final HeapSnapshot heapSnapshot;
        ActivityLeakResult result;
        final ExcludedRefs excludedRefs = AndroidExcludedRefs.createAppDefaults(Build.VERSION.SDK_INT, Build.MANUFACTURER).build();
        try {
            heapSnapshot = new HeapSnapshot(hprofFile);
            result = new ActivityLeakAnalyzer(referenceKey, excludedRefs).analyze(heapSnapshot);
        } catch (IOException e) {
            result = ActivityLeakResult.failure(e, 0);
        }
        return result;
    }
}
