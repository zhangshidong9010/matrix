package com.tencent.matrix.trace.util;

import android.util.Log;

import com.tencent.matrix.trace.constants.Constants;
import com.tencent.matrix.trace.core.AppMethodBeat;
import com.tencent.matrix.trace.items.MethodItem;
import com.tencent.matrix.util.MatrixLog;

import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;

public class TraceDataUtils {

    private static final String TAG = "Matrix.TraceDataUtils";

    public interface IStructuredDataFilter {
        boolean isFilter(long during, int filterCount);

        int getFilterMaxCount();

        void fallback(List<MethodItem> stack, int size);
    }

    public static void structuredDataToStack(long[] buffer, LinkedList<MethodItem> result, boolean isStrict, long endTime) {//这个方法主要是根据之前 data 查到的 methodId ，拿到对应插桩函数的执行时间、执行深度，将每个函数的信息封装成 MethodItem，然后存储到 stack 链表当中
        long lastInId = 0L;
        int depth = 0; //记录调用栈深度
        LinkedList<Long> rawData = new LinkedList<>();
        boolean isBegin = !isStrict;

        for (long trueId : buffer) {
            if (0 == trueId) {
                continue;
            }
            if (isStrict) {
                if (isIn(trueId) && AppMethodBeat.METHOD_ID_DISPATCH == getMethodId(trueId)) {
                    isBegin = true;
                }

                if (!isBegin) {
                    MatrixLog.d(TAG, "never begin! pass this method[%s]", getMethodId(trueId));
                    continue;
                }

            }
            if (isIn(trueId)) {
                lastInId = getMethodId(trueId);
                if (lastInId == AppMethodBeat.METHOD_ID_DISPATCH) {//如果是 handler 的 dispatchMessage 方法 depth 置为0
                    depth = 0;
                }
                depth++;
                rawData.push(trueId);//加入到链表中
            } else {// 如果是 0 方法记录的数据
                int outMethodId = getMethodId(trueId);
                if (!rawData.isEmpty()) {
                    long in = rawData.pop();//拿到i 方法中记录的数据
                    depth--;
                    int inMethodId;
                    LinkedList<Long> tmp = new LinkedList<>();
                    tmp.add(in);
                    while ((inMethodId = getMethodId(in)) != outMethodId && !rawData.isEmpty()) {//如果  inMethodId 不等于 outMethodId 调用深度建议
                        MatrixLog.w(TAG, "pop inMethodId[%s] to continue match ouMethodId[%s]", inMethodId, outMethodId);
                        in = rawData.pop();
                        depth--;
                        tmp.add(in);
                    }

                    if (inMethodId != outMethodId && inMethodId == AppMethodBeat.METHOD_ID_DISPATCH) {//如果是 handler的 dispatchMessage方法
                        MatrixLog.e(TAG, "inMethodId[%s] != outMethodId[%s] throw this outMethodId!", inMethodId, outMethodId);
                        rawData.addAll(tmp);
                        depth += rawData.size();
                        continue;
                    }

                    long outTime = getTime(trueId);//获取到 方法执行完的时间
                    long inTime = getTime(in);// 获取方法开始执行的时间
                    long during = outTime - inTime;//该方法执行时间
                    if (during < 0) {
                        MatrixLog.e(TAG, "[structuredDataToStack] trace during invalid:%d", during);
                        rawData.clear();
                        result.clear();
                        return;
                    }
                    MethodItem methodItem = new MethodItem(outMethodId, (int) during, depth);//创建一个 methodItem 并加入
                    addMethodItem(result, methodItem);
                } else {
                    MatrixLog.w(TAG, "[structuredDataToStack] method[%s] not found in! ", outMethodId);
                }
            }
        }

        while (!rawData.isEmpty() && isStrict) {
            long trueId = rawData.pop();
            int methodId = getMethodId(trueId);
            boolean isIn = isIn(trueId);
            long inTime = getTime(trueId) + AppMethodBeat.getDiffTime();
            MatrixLog.w(TAG, "[structuredDataToStack] has never out method[%s], isIn:%s, inTime:%s, endTime:%s,rawData size:%s",
                    methodId, isIn, inTime, endTime, rawData.size());
            if (!isIn) {
                MatrixLog.e(TAG, "[structuredDataToStack] why has out Method[%s]? is wrong! ", methodId);
                continue;
            }
            MethodItem methodItem = new MethodItem(methodId, (int) (endTime - inTime), rawData.size());
            addMethodItem(result, methodItem);
        }
        TreeNode root = new TreeNode(null, null);
        stackToTree(result, root);//将链表转为树 进行整理数据，root是根节点
        result.clear();//清空 result
        treeToStack(root, result);//将 整理过的 数据 保存到 result中
    }

    private static boolean isIn(long trueId) {
        return ((trueId >> 63) & 0x1) == 1;
    }

    private static long getTime(long trueId) {
        return trueId & 0x7FFFFFFFFFFL;
    }

    private static int getMethodId(long trueId) {
        return (int) ((trueId >> 43) & 0xFFFFFL);
    }

    private static int addMethodItem(LinkedList<MethodItem> resultStack, MethodItem item) {
        if (AppMethodBeat.isDev) {
            Log.v(TAG, "method:" + item);
        }
        MethodItem last = null;
        if (!resultStack.isEmpty()) {
            last = resultStack.peek();
        }
        if (null != last && last.methodId == item.methodId && last.depth == item.depth && 0 != item.depth) {
            item.durTime = item.durTime == Constants.DEFAULT_ANR ? last.durTime : item.durTime;
            last.mergeMore(item.durTime);
            return last.durTime;
        } else {
            resultStack.push(item);
            return item.durTime;
        }
    }

    private static void rechange(TreeNode root) {
        if (root.children.isEmpty()) {
            return;
        }
        TreeNode[] nodes = new TreeNode[root.children.size()];
        root.children.toArray(nodes);
        root.children.clear();
        for (TreeNode node : nodes) {
            root.children.addFirst(node);
            rechange(node);
        }
    }

    private static void treeToStack(TreeNode root, LinkedList<MethodItem> list) {

        for (int i = 0; i < root.children.size(); i++) {
            TreeNode node = root.children.get(i);
            if (node.item != null) {
                list.add(node.item);
            }
            if (!node.children.isEmpty()) {
                treeToStack(node, list);
            }
        }
    }


    /**
     * Structured the method stack as a tree Data structure
     *
     * @param resultStack
     * @return
     */
    public static int stackToTree(LinkedList<MethodItem> resultStack, TreeNode root) {
        TreeNode lastNode = null;
        ListIterator<MethodItem> iterator = resultStack.listIterator(0);
        int count = 0;
        while (iterator.hasNext()) {
            TreeNode node = new TreeNode(iterator.next(), lastNode);
            count++;
            if (null == lastNode && node.depth() != 0) {
                MatrixLog.e(TAG, "[stackToTree] begin error! why the first node'depth is not 0!");
                return 0;
            }
            int depth = node.depth();
            if (lastNode == null || depth == 0) {
                root.add(node);
            } else if (lastNode.depth() >= depth) {
                while (null != lastNode && lastNode.depth() > depth) {
                    lastNode = lastNode.father;
                }
                if (lastNode != null && lastNode.father != null) {
                    node.father = lastNode.father;
                    lastNode.father.add(node);
                }
            } else {
                lastNode.add(node);
            }
            lastNode = node;
        }
        return count;
    }


    public static long stackToString(LinkedList<MethodItem> stack, StringBuilder reportBuilder, StringBuilder logcatBuilder) {
        logcatBuilder.append("|*\t\tTraceStack:").append("\n");
        logcatBuilder.append("|*\t\t[id count cost]").append("\n");
        Iterator<MethodItem> listIterator = stack.iterator();
        long stackCost = 0; // fix cost
        while (listIterator.hasNext()) {
            MethodItem item = listIterator.next();
            reportBuilder.append(item.toString()).append('\n');
            logcatBuilder.append("|*\t\t").append(item.print()).append('\n');

            if (stackCost < item.durTime) {
                stackCost = item.durTime;
            }
        }
        return stackCost;
    }


    public static int countTreeNode(TreeNode node) {
        int count = node.children.size();
        Iterator<TreeNode> iterator = node.children.iterator();
        while (iterator.hasNext()) {
            count += countTreeNode(iterator.next());
        }
        return count;
    }

    /**
     * it's the node for the stack tree
     */
    public static final class TreeNode {
        MethodItem item;
        TreeNode father;

        LinkedList<TreeNode> children = new LinkedList<>();

        TreeNode(MethodItem item, TreeNode father) {
            this.item = item;
            this.father = father;
        }

        private int depth() {
            return null == item ? 0 : item.depth;
        }

        private void add(TreeNode node) {
            children.addFirst(node);
        }

        private boolean isLeaf() {
            return children.isEmpty();
        }
    }

    public static void printTree(TreeNode root, StringBuilder print) {
        print.append("|*   TraceStack: ").append("\n");
        printTree(root, 0, print, "|*        ");
    }

    public static void printTree(TreeNode root, int depth, StringBuilder ss, String prefixStr) {

        StringBuilder empty = new StringBuilder(prefixStr);

        for (int i = 0; i <= depth; i++) {
            empty.append("    ");
        }
        for (int i = 0; i < root.children.size(); i++) {
            TreeNode node = root.children.get(i);
            ss.append(empty.toString()).append(node.item.methodId).append("[").append(node.item.durTime).append("]").append("\n");
            if (!node.children.isEmpty()) {
                printTree(node, depth + 1, ss, prefixStr);
            }
        }
    }


    public static void trimStack(List<MethodItem> stack, int targetCount, IStructuredDataFilter filter) {//这个方法主要是 通过我们自定义的规则裁剪 stack 中的数据
        if (0 > targetCount) {
            stack.clear();
            return;
        }

        int filterCount = 1;
        int curStackSize = stack.size();
        while (curStackSize > targetCount) {
            ListIterator<MethodItem> iterator = stack.listIterator(stack.size());
            while (iterator.hasPrevious()) {
                MethodItem item = iterator.previous();
                if (filter.isFilter(item.durTime, filterCount)) {
                    iterator.remove();
                    curStackSize--;
                    if (curStackSize <= targetCount) {
                        return;
                    }
                }
            }
            curStackSize = stack.size();
            filterCount++;
            if (filter.getFilterMaxCount() < filterCount) {
                break;
            }
        }
        int size = stack.size();
        if (size > targetCount) {//如果 stack的 容量还是 大于 阈值，则使用降级策略
            filter.fallback(stack, size);
        }
    }

    @Deprecated
    public static String getTreeKey(List<MethodItem> stack, final int targetCount) {//这个方法主要是 获取耗时方法的 methodId拼接成的字符串
        StringBuilder ss = new StringBuilder();
        final List<MethodItem> tmp = new LinkedList<>(stack);
        trimStack(tmp, targetCount, new TraceDataUtils.IStructuredDataFilter() {
            @Override
            public boolean isFilter(long during, int filterCount) {
                return during < filterCount * Constants.TIME_UPDATE_CYCLE_MS;
            }

            @Override
            public int getFilterMaxCount() {
                return Constants.FILTER_STACK_MAX_COUNT;
            }

            @Override
            public void fallback(List<MethodItem> stack, int size) {
                MatrixLog.w(TAG, "[getTreeKey] size:%s targetSize:%s", size, targetCount);
                Iterator iterator = stack.listIterator(Math.min(size, targetCount));
                while (iterator.hasNext()) {
                    iterator.next();
                    iterator.remove();
                }
            }
        });
        for (MethodItem item : tmp) {//拼接字符串
            ss.append(item.methodId + "|");
        }
        return ss.toString();
    }

    public static String getTreeKey(List<MethodItem> stack, long stackCost) {
        StringBuilder ss = new StringBuilder();
        long allLimit = (long) (stackCost * Constants.FILTER_STACK_KEY_ALL_PERCENT);

        LinkedList<MethodItem> sortList = new LinkedList<>();

        for (MethodItem item : stack) {
            if (item.durTime >= allLimit) {
                sortList.add(item);
            }
        }

        Collections.sort(sortList, new Comparator<MethodItem>() {
            @Override
            public int compare(MethodItem o1, MethodItem o2) {
                return Integer.compare((o2.depth + 1) * o2.durTime, (o1.depth + 1) * o1.durTime);
            }
        });

        if (sortList.isEmpty() && !stack.isEmpty()) {
            MethodItem root = stack.get(0);
            sortList.add(root);
        } else if (sortList.size() > 1 && sortList.peek().methodId == AppMethodBeat.METHOD_ID_DISPATCH) {
            sortList.removeFirst();
        }

        for (MethodItem item : sortList) {
            ss.append(item.methodId + "|");
            break;
        }
        return ss.toString();
    }


}
