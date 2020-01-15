package com.tencent.matrix.report;


import java.util.Vector;

public interface IIDKeyReportListener {

    class IDKeyInfo {

        private long id = 0;
        private long key = 0;
        private long value = 0;

        public IDKeyInfo() {
        }

        public IDKeyInfo(long id, long key, long value) {
            this.id = id;
            this.key =key;
            this.value = value;
        }

        @Override
        public String toString() {
            return "id:" + id + ", key:" + key + ", value:" + value;
        }

        public void setId(long id) {
            this.id = id;
        }

        public void setKey(long key) {
            this.key = key;
        }

        public void setValue(long value) {
            this.value = value;
        }

        public long getId() {
            return id;
        }

        public long getKey() {
            return key;
        }

        public long getValue() {
            return value;
        }
    }

    void report(long  id,  long key, long value);
    void groupReport(final Vector<IDKeyInfo> idKeyInfos);
}
