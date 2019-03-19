package com.tencent.matrix.trace;

public class Configuration {

    public String packageName;
    public String mappingDir;
    public String baseMethodMapPath;
    public String methodMapFilePath;
    public String ignoreMethodMapFilePath;
    public String blackListFilePath;

    Configuration(String packageName, String mappingDir, String baseMethodMapPath, String methodMapFilePath, String ignoreMethodMapFilePath, String blackListFilePath) {
        this.packageName = packageName;
        this.mappingDir = mappingDir;
        this.baseMethodMapPath = baseMethodMapPath;
        this.methodMapFilePath = methodMapFilePath;
        this.ignoreMethodMapFilePath = ignoreMethodMapFilePath;
        this.blackListFilePath = blackListFilePath;
    }

    @Override
    public String toString() {
        return "\n# Configuration" + "\n" +
                "|* packageName:\t" + packageName + "\n" +
                "|* mappingDir:\t" + mappingDir + "\n" +
                "|* baseMethodMapPath:\t" + baseMethodMapPath + "\n" +
                "|* methodMapFilePath:\t" + methodMapFilePath + "\n" +
                "|* ignoreMethodMapFilePath:\t" + ignoreMethodMapFilePath + "\n" +
                "|* blackListFilePath:\t" + blackListFilePath + "\n";
    }

    public static class Builder {

        public String packageName;
        public String mappingPath;
        public String baseMethodMap;
        public String methodMapFile;
        public String ignoreMethodMapFile;
        public String blackListFile;

        public Builder setPackageName(String packageName) {
            this.packageName = packageName;
            return this;
        }

        public Builder setMappingPath(String mappingPath) {
            this.mappingPath = mappingPath;
            return this;
        }

        public Builder setBaseMethodMap(String baseMethodMap) {
            this.baseMethodMap = baseMethodMap;
            return this;
        }

        public Builder setMethodMapFilePath(String methodMapDir) {
            methodMapFile = methodMapDir;
            return this;
        }

        public Builder setIgnoreMethodMapFilePath(String methodMapDir) {
            ignoreMethodMapFile = methodMapDir;
            return this;
        }

        public Builder setBlackListFile(String blackListFile) {
            this.blackListFile = blackListFile;
            return this;
        }

        public Configuration build() {
            return new Configuration(packageName, mappingPath, baseMethodMap, methodMapFile, ignoreMethodMapFile, blackListFile);
        }

    }
}
