/**
 * Copyright 2017 The GreyCat Authors.  All rights reserved.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package greycat.structure.trees;

import greycat.Graph;
import greycat.Type;
import greycat.base.BaseNode;
import greycat.plugin.NodeState;
import greycat.plugin.NodeStateCallback;
import greycat.struct.DMatrix;
import greycat.struct.EGraph;
import greycat.struct.ENode;
import greycat.struct.LMatrix;
import greycat.structure.Tree;
import greycat.structure.TreeResult;
import greycat.structure.distance.Distance;
import greycat.structure.distance.Distances;
import greycat.structure.util.TreeHelper;
import greycat.structure.util.VolatileResult;

public class NDTree extends BaseNode implements Tree {

    public static String NAME = "NDTree";

    public static String BOUND_MIN = "bound_min";
    public static String BOUND_MAX = "bound_max";
    public static String RESOLUTION = "resolution";
    public static String BUFFER_SIZE = "buffer_size";
    public static String DISTANCE = "distance";

    private static String EGRAPH = "egraph";
    private static String STRATEGY = "strategy";

    public static int BUFFER_SIZE_DEF = 20;
    public static int DISTANCE_DEF = Distances.DEFAULT;

    private static int E_TOTAL = 0;
    private static int E_SUBNODES = 1;
    private static int E_TOTAL_SUBNODES = 2;
    private static int E_MIN = 3;
    private static int E_MAX = 4;
    private static int E_BUFFER_KEYS = 5;
    private static int E_BUFFER_VALUES = 6;
    private static int E_VALUE = 7;
    private static int E_PROFILE = 8;
    private static int E_OFFSET_REL = 16;

    public NDTree(final long p_world, final long p_time, final long p_id, final Graph p_graph) {
        super(p_world, p_time, p_id, p_graph);
    }

    private static int getRelationId(double[] centerKey, double[] keyToInsert) {
        int result = 0;
        for (int i = 0; i < centerKey.length; i++) {
            if (i != 0) {
                result = result << 1;
            }
            if (keyToInsert[i] > centerKey[i]) {
                result += 1;
            }
        }
        return result + E_OFFSET_REL;
    }

    private static boolean checkCreateLevels(double[] min, double[] max, double[] resolutions) {
        if (resolutions != null) {
            for (int i = 0; i < min.length; i++) {
                if ((max[i] - min[i]) > 2 * resolutions[i]) {
                    return true;
                }
            }
        } else {
            for (int i = 0; i < min.length; i++) {
                if ((max[i] > min[i])) {
                    return true;
                }
            }
        }
        return false;
    }

    private static double[] getCenterMinMax(double[] min, double[] max) {
        double[] center = new double[min.length];
        for (int i = 0; i < min.length; i++) {
            center[i] = (max[i] + min[i]) / 2;
        }
        return center;
    }

    private static double[] getCenter(ENode node) {
        double[] min = (double[]) node.getAt(E_MIN);
        double[] max = (double[]) node.getAt(E_MAX);
        return getCenterMinMax(min, max);
    }

    private static void check(double[] values, double[] min, double[] max) {
        if (min == null || max == null) {
            throw new RuntimeException("Please set min and max boundary before inserting in the trees");
        }
        if (values.length != min.length) {
            throw new RuntimeException("Values dimension mismatch");
        }
        for (int i = 0; i < min.length; i++) {
            if (values[i] < min[i] || values[i] > max[i]) {
                throw new RuntimeException("Values should be between min, max!");
            }
        }
    }

    private static ENode createNewNode(final ENode parent, final ENode root, final int index, final double[] min, final double[] max, final double[] center, final double[] keyToInsert, final int buffersize) {
        ENode node = parent.graph().newNode();
        double[] minChild = new double[min.length];
        double[] maxChild = new double[max.length];
        for (int i = 0; i < min.length; i++) {
            if (keyToInsert[i] <= center[i]) {
                minChild[i] = min[i];
                maxChild[i] = center[i];
            } else {
                minChild[i] = center[i];
                maxChild[i] = max[i];
            }
        }
        node.setAt(E_SUBNODES, Type.LONG, 0);
        node.setAt(E_MIN, Type.DOUBLE_ARRAY, minChild);
        node.setAt(E_MAX, Type.DOUBLE_ARRAY, maxChild);
        node.setAt(E_TOTAL, Type.LONG, 0);
        root.setAt(E_TOTAL_SUBNODES, Type.LONG, (long) root.getAt(E_TOTAL_SUBNODES) + 1);
        parent.setAt(E_SUBNODES, Type.LONG, (long) parent.getAt(E_SUBNODES) + 1);
        parent.setAt(index, Type.ENODE, node);
        //todo create buffer here
       /* if (buffersize != 0) {

        }*/
        return node;
    }

    private static boolean subInsert(final ENode parent, final double[] key, final long value, final int strategyType, final double[] min, final double[] max, final double[] center, final double[] resolution, final int buffersize, final ENode root, boolean bufferupdate) {
        int index = getRelationId(center, key);

        ENode child = (ENode) parent.getAt(index);
        if (child == null) {
            child = createNewNode(parent, root, index, min, max, center, key, buffersize);
        }
        double[] childmin = (double[]) child.getAt(E_MIN);
        double[] childmax = (double[]) child.getAt(E_MAX);
        double[] childcenter = getCenterMinMax(childmin, childmax);
        boolean res = internalInsert(child, key, value, strategyType, childmin, childmax, childcenter, resolution, buffersize, root);
        res = res && !bufferupdate;

        if (res) {
            switch (strategyType) {
                case IndexStrategy.PROFILE: {
                    parent.setAt(E_TOTAL, Type.LONG, (long) parent.getAt(E_TOTAL) + (long) value);
                    break;
                }
                case IndexStrategy.INDEX: {
                    parent.setAt(E_TOTAL, Type.LONG, (long) parent.getAt(E_TOTAL) + 1);
                    break;
                }
                default: {
                    throw new RuntimeException("Index strategy is wrong!");
                }
            }
        }
        return res;
    }

    private static boolean internalInsert(final ENode node, final double[] key, final long value, final int strategyType, final double[] min, final double[] max, final double[] center, final double[] resolution, final int buffersize, final ENode root) {
        if ((long) node.getAt(E_SUBNODES) != 0) {
            return subInsert(node, key, value, strategyType, min, max, center, resolution, buffersize, root, false);
        } else if (checkCreateLevels(min, max, resolution)) {
            DMatrix buffer = null;
            if (buffersize > 0) {
                buffer = (DMatrix) node.getOrCreateAt(E_BUFFER_KEYS, Type.DMATRIX);
            }
            if (buffer != null) {
                //First step check if it already exists in the buffer
                for (int i = 0; i < buffer.columns(); i++) {
                    if (compare(key, buffer.column(i), resolution)) {
                        switch (strategyType) {
                            case IndexStrategy.PROFILE: {
                                DMatrix bufferkeys = (DMatrix) node.getAt(E_PROFILE);
                                for (int j = 0; j < key.length; j++) {
                                    bufferkeys.set(j, i, bufferkeys.get(j, i) + key[j] * value);
                                }
                                LMatrix bufferValue = (LMatrix) node.getAt(E_BUFFER_VALUES);
                                bufferValue.set(0, i, bufferValue.get(0, i) + value);
                                node.setAt(E_TOTAL, Type.LONG, (long) node.getAt(E_TOTAL) + value);
                                return true; //to update parent total
                            }
                            case IndexStrategy.INDEX: {
                                LMatrix bufferValue = (LMatrix) node.getAt(E_BUFFER_VALUES);
                                bufferValue.set(0, i, value);
                                return false; //Should not update parent total
                            }
                            default: {
                                throw new RuntimeException("Index strategy is wrong!");
                            }
                        }
                    }
                }
                //Here it is not in the buffer, we check if we can append
                if (buffer.columns() < buffersize) {
                    buffer.appendColumn(key);
                    switch (strategyType) {
                        case IndexStrategy.PROFILE: {
                            DMatrix bufferkeys = (DMatrix) node.getOrCreateAt(E_PROFILE, Type.DMATRIX);
                            bufferkeys.appendColumn(key);
                            LMatrix bufferValue = (LMatrix) node.getOrCreateAt(E_BUFFER_VALUES, Type.LMATRIX);
                            bufferValue.appendColumn(new long[]{value});
                            node.setAt(E_TOTAL, Type.LONG, (long) node.getAt(E_TOTAL) + value);
                            return true; //to update parent total
                        }

                        case IndexStrategy.INDEX: {
                            LMatrix bufferValue = (LMatrix) node.getOrCreateAt(E_BUFFER_VALUES, Type.LMATRIX);
                            bufferValue.appendColumn(new long[]{(long) value});
                            node.setAt(E_TOTAL, Type.LONG, (long) node.getAt(E_TOTAL) + 1);
                            return true; //to update parent total
                        }
                        default: {
                            throw new RuntimeException("Index strategy is wrong!");
                        }
                    }
                }
                //here buffer is full we need to reinsert
                else {
                    //if it is a profile, get the average of all the keys and update the buffer before reinserting
                    if (strategyType == IndexStrategy.PROFILE) {
                        DMatrix bufferkeys = (DMatrix) node.getAt(E_PROFILE);
                        LMatrix bufferValue = (LMatrix) node.getAt(E_BUFFER_VALUES);
                        for (int i = 0; i < buffer.columns(); i++) {
                            long t = bufferValue.get(0, i);
                            for (int j = 0; j < buffer.rows(); j++) {
                                buffer.set(j, i, bufferkeys.get(j, i) / t);
                            }
                        }
                        node.setAt(E_PROFILE, Type.DMATRIX, null);
                    }


                    //reinsert all children

                    LMatrix bufferValue = (LMatrix) node.getAt(E_BUFFER_VALUES);
                    for (int i = 0; i < buffer.columns(); i++) {
                        subInsert(node, buffer.column(i), bufferValue.get(0, i), strategyType, min, max, center, resolution, buffersize, root, true);
                    }
                    node.setAt(E_BUFFER_VALUES, Type.LMATRIX, null);

                    //clear the buffer, update the total, and insert the new value
                    node.setAt(E_BUFFER_KEYS, Type.DMATRIX, null);
                    return subInsert(node, key, value, strategyType, min, max, center, resolution, buffersize, root, false);

                }


            } //null buffer means to subinsert as long as we can create levels
            else {
                return subInsert(node, key, value, strategyType, min, max, center, resolution, buffersize, root, false);
            }
        }
        //Else we reached here last level of the trees, and the array is full, we need to start a profiler
        else {
            switch (strategyType) {
                case IndexStrategy.PROFILE: {
                    //todo add the value later
                    double[] profile = (double[]) node.getAt(E_PROFILE);
                    if (profile == null) {
                        profile = new double[key.length];
                        System.arraycopy(key, 0, profile, 0, key.length);
                    } else {
                        for (int i = 0; i < key.length; i++) {
                            profile[i] += key[i] * value;
                        }
                    }
                    node.setAt(E_PROFILE, Type.DOUBLE_ARRAY, profile);
                    node.setAt(E_TOTAL, Type.LONG, (long) node.getAt(E_TOTAL) + value);
                    return true; //to update parent total
                }
                case IndexStrategy.INDEX: {
                    if ((long) node.getAt(E_TOTAL) == 0) {
                        node.setAt(E_PROFILE, Type.DOUBLE_ARRAY, key);
                        node.setAt(E_VALUE, Type.LONG, value);
                        node.setAt(E_TOTAL, Type.LONG, 1);
                        return true;
                    } else {
                        node.setAt(E_PROFILE, Type.DOUBLE_ARRAY, key);
                        node.setAt(E_VALUE, Type.LONG, value);
                        return false;
                    }
                }
                default: {
                    throw new RuntimeException("Index strategy is wrong!");
                }
            }

        }

    }

    private static boolean compare(double[] key1, double[] key2, double[] resolution) {
        //todo compare with distance
        if (resolution != null) {
            for (int i = 0; i < key1.length; i++) {
                if (Math.abs(key1[i] - key2[i]) > resolution[i]) {
                    return false;
                }
            }
        } else {
            for (int i = 0; i < key1.length; i++) {
                if (Math.abs(key1[i] - key2[i]) > 0) {
                    return false;
                }
            }
        }
        return true;
    }

    @Override
    public void setDistance(int distanceType) {
        super.set(DISTANCE, Type.INT, distanceType);
    }


    @Override
    public void insert(final double[] keys, final long value) {
        final NodeState state = unphasedState();
        double[] min = (double[]) state.getFromKey(BOUND_MIN);
        double[] max = (double[]) state.getFromKey(BOUND_MAX);
        check(keys, min, max);
        double[] resolution = (double[]) state.getFromKey(RESOLUTION);
        EGraph graph = (EGraph) state.getOrCreateFromKey(EGRAPH, Type.EGRAPH);
        int buffersize = state.getFromKeyWithDefault(BUFFER_SIZE, BUFFER_SIZE_DEF);
        //Distance distance = Distances.getDistance(state.getWithDefault(DISTANCE, DISTANCE_DEF));
        synchronized (state) { // assumption that NodeState == StateChunk
            ENode root = graph.root();
            if (root == null) {
                root = graph.newNode();
                state.setFromKey(STRATEGY, Type.INT, IndexStrategy.INDEX);
                graph.setRoot(root);
                root.setAt(E_TOTAL, Type.LONG, 0);
                root.setAt(E_TOTAL_SUBNODES, Type.LONG, 0);
                root.setAt(E_SUBNODES, Type.LONG, 0);
                root.setAt(E_MIN, Type.DOUBLE_ARRAY, min);
                root.setAt(E_MAX, Type.DOUBLE_ARRAY, max);
            }
            internalInsert(root, keys, value, IndexStrategy.INDEX, min, max, getCenterMinMax(min, max), resolution, buffersize, root);
        }
    }

    @Override
    public void profile(final double[] keys, final long occurrence) {
        NodeState state = unphasedState();
        double[] min = (double[]) state.getFromKey(BOUND_MIN);
        double[] max = (double[]) state.getFromKey(BOUND_MAX);
        check(keys, min, max);
        double[] resolution = (double[]) state.getFromKey(RESOLUTION);
        EGraph graph = (EGraph) state.getOrCreateFromKey(EGRAPH, Type.EGRAPH);
        int buffersize = state.getFromKeyWithDefault(BUFFER_SIZE, BUFFER_SIZE_DEF);
        //Distance distance = Distances.getDistance(state.getWithDefault(DISTANCE, DISTANCE_DEF));
        synchronized (state) {
            ENode root = graph.root();
            if (root == null) {
                root = graph.newNode();
                state.setFromKey(STRATEGY, Type.INT, IndexStrategy.PROFILE);
                graph.setRoot(root);
                root.setAt(E_TOTAL, Type.LONG, 0);
                root.setAt(E_TOTAL_SUBNODES, Type.LONG, 0);
                root.setAt(E_SUBNODES, Type.LONG, 0);
                root.setAt(E_MIN, Type.DOUBLE_ARRAY, min);
                root.setAt(E_MAX, Type.DOUBLE_ARRAY, max);
            }
            internalInsert(root, keys, occurrence, IndexStrategy.PROFILE, min, max, getCenterMinMax(min, max), resolution, buffersize, root);
        }
    }

    @Override
    public TreeResult nearestN(double[] keys, int nbElem) {
        return nearestNWithinRadius(keys, -1, -1);
    }

    @Override
    public TreeResult nearestWithinRadius(double[] keys, double radius) {
        return nearestNWithinRadius(keys, -1, radius);
    }

    @Override
    public TreeResult nearestNWithinRadius(double[] keys, int nbElem, double radius) {
        NodeState state = unphasedState();

        double[] min = (double[]) state.getFromKey(BOUND_MIN);
        double[] max = (double[]) state.getFromKey(BOUND_MAX);
        check(keys, min, max);

        EGraph graph = (EGraph) state.getOrCreateFromKey(EGRAPH, Type.EGRAPH);
        Distance distance = Distances.getDistance(state.getFromKeyWithDefault(DISTANCE, DISTANCE_DEF));
        int strategyType = (int) state.getFromKey(STRATEGY);


        ENode root = graph.root();
        if (root == null) {
            return null;
        }

        EGraph calcZone = graph().space().newVolatileGraph();
        VolatileResult nnl = new VolatileResult(calcZone.newNode(), nbElem);
        reccursiveTraverse(root, calcZone, nnl, strategyType, distance, keys, null, null, null, radius);
        nnl.sort(true);
        return nnl;

    }


    @Override
    public TreeResult query(double[] min, double[] max) {
        NodeState state = unphasedState();

        EGraph graph = (EGraph) state.getOrCreateFromKey(EGRAPH, Type.EGRAPH);
        Distance distance = Distances.getDistance(state.getFromKeyWithDefault(DISTANCE, DISTANCE_DEF));
        int strategyType = (int) state.getFromKey(STRATEGY);

        final double[] center = new double[max.length];

        for (int i = 0; i < center.length; i++) {
            center[i] = (min[i] + max[i]) / 2;
        }

        ENode root = graph.root();
        if (root == null) {
            return null;
        }

        EGraph calcZone = graph().space().newVolatileGraph();
        VolatileResult nnl = new VolatileResult(calcZone.newNode(), -1);
        reccursiveTraverse(root, calcZone, nnl, strategyType, distance, null, min, max, center, -1);

        nnl.sort(true);
        return nnl;
    }


    @Override
    public long size() {
        NodeState state = unphasedState();
        EGraph graph = (EGraph) state.getOrCreateFromKey(EGRAPH, Type.EGRAPH);
        ENode root = graph.root();
        if (root == null) {
            return 0;
        } else {
            return (long) root.getAt(E_TOTAL);
        }
    }

    @Override
    public long numberOfNodes() {
        NodeState state = unphasedState();
        EGraph graph = (EGraph) state.getOrCreateFromKey(EGRAPH, Type.EGRAPH);
        ENode root = graph.root();
        if (root == null) {
            return 0;
        } else {
            return (long) root.getAt(E_TOTAL_SUBNODES);
        }
    }


    private static boolean[] binaryFromLong(long value, int dim) {
        long tempvalue = value - E_OFFSET_REL;
        long shiftvalue = tempvalue >> 1;
        boolean[] res = new boolean[dim];
        for (int i = 0; i < dim; i++) {
            res[dim - i - 1] = ((tempvalue - (shiftvalue << 1)) == 1);
            tempvalue = shiftvalue;
            shiftvalue = tempvalue >> 1;
        }
        return res;
    }


    private static void reccursiveTraverse(final ENode node, final EGraph calcZone, final VolatileResult nnl, final int strategyType, final Distance distance, final double[] target, final double[] targetmin, final double[] targetmax, final double[] targetcenter, final double radius) {

        if ((long) node.getAt(E_SUBNODES) == 0) {
            //Leave node
            DMatrix buffer = (DMatrix) node.getAt(E_BUFFER_KEYS);
            LMatrix bufferValue = (LMatrix) node.getAt(E_BUFFER_VALUES);

            if (buffer != null) {
                //Bufferizing node
                switch (strategyType) {
                    case IndexStrategy.PROFILE: {
                        double[] tempK = new double[target.length];

                        DMatrix bufferkeys = (DMatrix) node.getAt(E_PROFILE);
                        for (int i = 0; i < buffer.columns(); i++) {
                            long t = bufferValue.get(0, i);
                            for (int j = 0; j < buffer.rows(); j++) {
                                tempK[j] = bufferkeys.get(j, i) / t;
                            }
                            TreeHelper.filterAndInsert(tempK, t, target, targetmin, targetmax, targetcenter, distance, radius, nnl);
                        }
                        return;
                    }
                    case IndexStrategy.INDEX: {
                        for (int i = 0; i < buffer.columns(); i++) {
                            TreeHelper.filterAndInsert(buffer.column(i), bufferValue.get(0, i), target, targetmin, targetmax, targetcenter, distance, radius, nnl);
                        }
                        return;

                    }
                    default: {
                        throw new RuntimeException("Index strategy is wrong!");
                    }
                }

            } else {
                //Very End node
                switch (strategyType) {
                    case IndexStrategy.PROFILE: {
                        double[] keyo = (double[]) node.getAt(E_PROFILE);
                        double[] key = new double[keyo.length];
                        long value = (long) node.getAt(E_TOTAL);
                        for (int i = 0; i < keyo.length; i++) {
                            key[i] = keyo[i] / value;
                        }
                        TreeHelper.filterAndInsert(key, value, target, targetmin, targetmax, targetcenter, distance, radius, nnl);
                        return;
                    }
                    case IndexStrategy.INDEX: {
                        double[] key = (double[]) node.getAt(E_PROFILE);
                        long value = (long) node.getAt(E_VALUE);
                        TreeHelper.filterAndInsert(key, value, target, targetmin, targetmax, targetcenter, distance, radius, nnl);
                        return;
                    }
                    default: {
                        throw new RuntimeException("Index strategy is wrong!");
                    }
                }
            }

        } else {
            //Parent node
            final double[] boundMax = (double[]) node.getAt(E_MAX);
            final double[] boundMin = (double[]) node.getAt(E_MIN);
            final double worst = nnl.getWorstDistance();

            if (targetmin == null || targetmax == null) {
                if (!nnl.isCapacityReached() || TreeHelper.getclosestDistance(target, boundMin, boundMax, distance) <= worst) {
                    final ENode tempList = calcZone.newNode();
                    final VolatileResult childPriority = new VolatileResult(tempList, -1);
                    final int dim = boundMax.length;
                    final double[] childMin = new double[dim];
                    final double[] childMax = new double[dim];

                    node.each(new NodeStateCallback() {
                        @Override
                        public void on(int attributeKey, byte elemType, Object elem) {
                            if (attributeKey >= E_OFFSET_REL) {
                                boolean[] binaries = binaryFromLong(attributeKey, dim);
                                for (int i = 0; i < dim; i++) {
                                    if (!binaries[i]) {
                                        childMin[i] = boundMin[i];
                                        childMax[i] = (boundMax[i] + boundMin[i]) / 2;

                                    } else {
                                        childMin[i] = (boundMax[i] + boundMin[i]) / 2;
                                        childMax[i] = boundMax[i];
                                    }
                                }
                                childPriority.insert(childMin, attributeKey, TreeHelper.getclosestDistance(target, childMin, childMax, distance));
                            }
                        }
                    });
                    childPriority.sort(true);

                    for (int i = 0; i < childPriority.size(); i++) {
                        ENode child = (ENode) node.getAt((int) childPriority.value(i));
                        reccursiveTraverse(child, calcZone, nnl, strategyType, distance, target, targetmin, targetmax, targetcenter, radius);
                    }
                    childPriority.free();
                }
            } else {
                node.each(new NodeStateCallback() {
                    @Override
                    public void on(int attributeKey, byte elemType, Object elem) {
                        if (attributeKey >= E_OFFSET_REL) {
                            ENode child = (ENode) node.getAt(attributeKey);
                            if (TreeHelper.checkBoundsIntersection(targetmin, targetmax, (double[]) child.getAt(E_MIN), (double[]) child.getAt(E_MAX))) {
                                reccursiveTraverse(child, calcZone, nnl, strategyType, distance, target, targetmin, targetmax, targetcenter, radius);
                            }
                        }
                    }
                });
            }
        }
    }


}