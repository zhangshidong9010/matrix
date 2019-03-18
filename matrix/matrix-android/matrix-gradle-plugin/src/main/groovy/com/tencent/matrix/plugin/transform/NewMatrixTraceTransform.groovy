package com.tencent.matrix.plugin.transform

import com.android.build.api.transform.DirectoryInput
import com.android.build.api.transform.JarInput
import com.android.build.api.transform.QualifiedContent
import com.android.build.api.transform.Transform
import com.android.build.api.transform.TransformException
import com.android.build.api.transform.TransformInput
import com.android.build.api.transform.TransformInvocation
import com.android.build.gradle.AppExtension
import com.android.build.gradle.internal.pipeline.TransformManager
import com.tencent.matrix.trace.Configuration
import com.android.build.api.transform.Format
import org.apache.commons.io.FileUtils
import org.gradle.api.Project

class NewMatrixTraceTransform extends Transform {
    def TAG = "NewMatrixTraceTransform"
    AppExtension android
    Project project

    NewMatrixTraceTransform(AppExtension android, Project project) {
        this.android = android
        this.project = project
    }

    @Override
    void transform(TransformInvocation transformInvocation) throws TransformException, InterruptedException, IOException {
        super.transform(transformInvocation)

        throw new RuntimeException("fdsfsdfasdfdfasdf"+variant.name())
//        android.applicationVariants.all { variant ->
//
//            def variantData = variant.variantData
//            def scope = variantData.scope
//            System.out.printf("-------------->scope:" + scope.getGenerateBuildConfigTask())
//
////            Configuration configuration = initConfig(project, variant)
//
//
//        }


        transformInvocation.inputs.each { TransformInput input ->
            //遍历文件夹
            input.directoryInputs.each { DirectoryInput directoryInput ->

                def dest = transformInvocation.outputProvider.getContentLocation(directoryInput.name, directoryInput.contentTypes, directoryInput.scopes, Format.DIRECTORY)
                // 将input的目录复制到output指定目录
                FileUtils.copyDirectory(directoryInput.file, dest)
            }

            //遍历jar文件 对jar不操作，但是要输出到out路径
            input.jarInputs.each { JarInput jarInput ->
                // 重命名输出文件（同名文件copyFile会冲突）
                def jarName = jarInput.name
                println("jar = " + jarInput.file.getAbsolutePath())
                def md5Name = System.currentTimeMillis()
                if (jarName.endsWith(".jar")) {
                    jarName = jarName.substring(0, jarName.length() - 4)
                }
                def dest = transformInvocation.outputProvider.getContentLocation(jarName + md5Name, jarInput.contentTypes, jarInput.scopes, Format.JAR)
                FileUtils.copyFile(jarInput.file, dest)
            }
        }
    }

    @Override
    String getName() {
        return "NewMatrixTraceTransform"
    }

    @Override
    Set<QualifiedContent.ContentType> getInputTypes() {
        return TransformManager.CONTENT_CLASS
    }

    @Override
    Set<QualifiedContent.Scope> getScopes() {
        return TransformManager.SCOPE_FULL_PROJECT
    }

    @Override
    boolean isIncremental() {
        return true
    }

    private Configuration initConfig(Project project, def variant) {
        def configuration = project.matrix
        def variantName = variant.name.capitalize()
        def mappingFilePath = ""
        if (variant.getBuildType().isMinifyEnabled()) {
            mappingFilePath = variant.mappingFile.getAbsolutePath()
        }
        Configuration config = new Configuration.Builder()
                .setPackageName(variant.applicationId)
                .setBaseMethodMap(configuration.trace.baseMethodMapFile)
                .setMethodMapDir(configuration.output + "/${variantName}.methodmap")
                .setIgnoreMethodMapDir(configuration.output + "/${variantName}.ignoremethodmap")
                .setBlackListFile(configuration.trace.blackListFile)
                .setMappingPath(mappingFilePath)
                .build()
        return config
    }


}
