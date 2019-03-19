package com.tencent.matrix.trace.transform;

import com.android.build.api.transform.QualifiedContent;
import com.android.build.api.transform.Transform;
import com.android.build.api.transform.TransformException;
import com.android.build.api.transform.TransformInvocation;
import com.android.build.gradle.internal.pipeline.TransformManager;
import com.android.build.gradle.internal.pipeline.TransformTask;
import com.android.build.gradle.internal.scope.GlobalScope;
import com.android.build.gradle.internal.scope.VariantScope;
import com.android.build.gradle.internal.variant.BaseVariantData;
import com.google.common.base.Joiner;
import com.tencent.matrix.javalib.util.Log;
import com.tencent.matrix.javalib.util.Util;
import com.tencent.matrix.trace.Configuration;
import com.tencent.matrix.trace.extension.MatrixTraceExtension;

import org.gradle.api.Project;
import org.gradle.api.Task;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Set;

import static com.android.builder.model.AndroidProject.FD_OUTPUTS;

public class MatrixTraceTransform extends Transform {

    private static final String TAG = "MatrixTraceTransform";
    private Configuration config;
    private Transform origTransform;

    public static void inject(Project project, MatrixTraceExtension extension, VariantScope variantScope) {

        GlobalScope globalScope = variantScope.getGlobalScope();
        BaseVariantData variant = variantScope.getVariantData();
        String mappingOut = Joiner.on(File.separatorChar).join(
                String.valueOf(globalScope.getBuildDir()),
                FD_OUTPUTS,
                "mapping",
                variantScope.getVariantConfiguration().getDirName());
        Configuration config = new Configuration.Builder()
                .setPackageName(variant.getApplicationId())
                .setBaseMethodMap(extension.getBaseMethodMapFile())
                .setBlackListFile(extension.getBlackListFile())
                .setMethodMapFilePath(mappingOut + "/methodMapping.txt")
                .setIgnoreMethodMapFilePath(mappingOut + "/ignoreMethodMapping.txt")
                .setMappingPath(mappingOut)
                .build();

        try {
            String[] hardTask = getTransformTaskName(extension.getCustomDexTransformName(), variant.getName());
            for (Task task : project.getTasks()) {
                for (String str : hardTask) {
                    if (task.getName().equalsIgnoreCase(str) && task instanceof TransformTask) {
                        TransformTask transformTask = (TransformTask) task;
                        Log.i(TAG, "successfully inject task:" + transformTask.getName());
                        Field field = TransformTask.class.getDeclaredField("transform");
                        field.setAccessible(true);
                        field.set(task, new MatrixTraceTransform(config, transformTask.getTransform()));
                        break;
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, e.toString());
        }

    }


    private static String[] getTransformTaskName(String customDexTransformName, String buildTypeSuffix) {
        if (!Util.isNullOrNil(customDexTransformName)) {
            return new String[]{customDexTransformName + "For" + "buildTypeSuffix"};
        } else {
            String[] names = new String[]{
                    "transformClassesWithDexBuilderFor" + buildTypeSuffix,
                    "transformClassesWithDexFor" + buildTypeSuffix,
            };
            return names;
        }
    }

    public MatrixTraceTransform(Configuration config, Transform origTransform) {
        this.config = config;
        this.origTransform = origTransform;
    }

    @Override
    public String getName() {
        return TAG;
    }

    @Override
    public Set<QualifiedContent.ContentType> getInputTypes() {
        return TransformManager.CONTENT_CLASS;
    }

    @Override
    public Set<QualifiedContent.Scope> getScopes() {
        return TransformManager.SCOPE_FULL_PROJECT;
    }

    @Override
    public boolean isIncremental() {
        return true;
    }

    @Override
    public void transform(TransformInvocation transformInvocation) throws TransformException, InterruptedException, IOException {
        super.transform(transformInvocation);

        doTransform(transformInvocation);

        origTransform.transform(transformInvocation);

    }

    // todo
    private void doTransform(TransformInvocation transformInvocation) throws TransformException, InterruptedException, IOException {



    }

}
