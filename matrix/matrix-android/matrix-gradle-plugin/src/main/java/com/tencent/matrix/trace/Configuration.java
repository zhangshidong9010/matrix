package com.tencent.matrix.trace;

public class Configuration {

    public String packageName;
    public String mappingPath;
    public String baseMethodMap;
    public String methodMapFile;
    public String ignoreMethodMapFile;
    public String blackListFile;

    Configuration(String packageName, String mappingPath, String baseMethodMap, String methodMapFile, String ignoreMethodMapFile, String blackListFile) {
        this.packageName = packageName;
        this.mappingPath = mappingPath;
        this.baseMethodMap = baseMethodMap;
        this.methodMapFile = methodMapFile;
        this.ignoreMethodMapFile = ignoreMethodMapFile;
        this.blackListFile = blackListFile;
    }

    @Override
    public String toString() {
        return "\n# Configuration" + "\n" +
                "|* packageName:\t" + packageName + "\n" +
                "|* mappingPath:\t" + mappingPath + "\n" +
                "|* baseMethodMap:\t" + baseMethodMap + "\n" +
                "|* methodMapFile:\t" + methodMapFile + "\n" +
                "|* ignoreMethodMapFile:\t" + ignoreMethodMapFile + "\n" +
                "|* blackListFile:\t" + blackListFile + "\n";
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

        public Builder setMethodMapDir(String methodMapDir) {
            methodMapFile = methodMapDir;
            return this;
        }

        public Builder setIgnoreMethodMapDir(String methodMapDir) {
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
