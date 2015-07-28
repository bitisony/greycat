
package org.kevoree.modeling.memory.struct.cache.impl;

import org.kevoree.modeling.KConfig;
import org.kevoree.modeling.KContentKey;
import org.kevoree.modeling.KObject;
import org.kevoree.modeling.memory.KMemoryElement;
import org.kevoree.modeling.memory.manager.impl.MemorySegmentResolutionTrace;
import org.kevoree.modeling.memory.manager.impl.ResolutionHelper;
import org.kevoree.modeling.memory.struct.cache.KCache;
import org.kevoree.modeling.memory.struct.chunk.impl.HeapMemoryChunk;
import org.kevoree.modeling.meta.KMetaModel;

public class ArrayMemoryCache implements KCache {

    private volatile int _elementCount;

    private volatile int _droppedCount;

    private volatile InternalState _state = null;

    private int _threshold;

    private final float _loadFactor;

    /**
     * @ignore ts
     */
    private KObjectWeakReference rootReference = null;

    private class InternalState {

        public final int elementDataSize;

        public final long[] elementK3;

        public final int[] elementNext;

        public final int[] elementHash;

        public final KMemoryElement[] values;

        public InternalState(int p_elementDataSize, long[] p_elementKE, int[] p_elementNext, int[] p_elementHash, KMemoryElement[] p_values) {
            this.elementDataSize = p_elementDataSize;
            this.elementK3 = p_elementKE;
            this.elementNext = p_elementNext;
            this.elementHash = p_elementHash;
            this.values = p_values;
        }
    }

    public ArrayMemoryCache() {
        int initialCapacity = KConfig.CACHE_INIT_SIZE;
        this._loadFactor = KConfig.CACHE_LOAD_FACTOR;
        this._elementCount = 0;
        this._droppedCount = 0;
        InternalState newstate = new InternalState(initialCapacity, new long[initialCapacity * 3], new int[initialCapacity], new int[initialCapacity], new KMemoryElement[initialCapacity]);
        for (int i = 0; i < initialCapacity; i++) {
            newstate.elementNext[i] = -1;
            newstate.elementHash[i] = -1;
        }
        this._state = newstate;
        this._threshold = (int) (newstate.elementDataSize * this._loadFactor);
    }

    private void rehashCapacity(int capacity) {
        int length = (capacity == 0 ? 1 : capacity << 1);
        long[] newElementKV = new long[length * 3];
        System.arraycopy(_state.elementK3, 0, newElementKV, 0, _state.elementDataSize * 3);
        KMemoryElement[] newValues = new KMemoryElement[length];
        System.arraycopy(_state.values, 0, newValues, 0, _state.elementDataSize);
        int[] newElementNext = new int[length];
        int[] newElementHash = new int[length];
        for (int i = 0; i < length; i++) {
            newElementNext[i] = -1;
            newElementHash[i] = -1;
        }
        //rehashEveryThing
        for (int i = 0; i < _state.elementDataSize; i++) {
            if (_state.values[i] != null) { //there is a real value
                int hash = (int) (_state.elementK3[(i * 3)] ^ _state.elementK3[(i * 3) + 1] ^ _state.elementK3[(i * 3) + 2]);
                int index = (hash & 0x7FFFFFFF) % length;
                newElementNext[i] = newElementHash[index];
                newElementHash[index] = i;
            }
        }
        //setPrimitiveType value for all
        _state = new InternalState(length, newElementKV, newElementNext, newElementHash, newValues);
        this._threshold = (int) (length * this._loadFactor);
    }

    @Override
    public final KMemoryElement get(long universe, long time, long obj) {
        InternalState internalState = _state;
        if (internalState.elementDataSize == 0) {
            return null;
        }
        int index = (((int) (universe ^ time ^ obj)) & 0x7FFFFFFF) % internalState.elementDataSize;
        int m = internalState.elementHash[index];
        while (m != -1) {
            if (universe == internalState.elementK3[(m * 3)] && time == internalState.elementK3[((m * 3) + 1)] && obj == internalState.elementK3[((m * 3) + 2)]) {
                return internalState.values[m]; /* getValue */
            } else {
                m = internalState.elementNext[m];
            }
        }
        return null;
    }

    @Override
    public final void putAndReplace(long universe, long time, long obj, KMemoryElement payload) {
        internal_put(universe, time, obj, payload, true);
    }

    @Override
    public final KMemoryElement getOrPut(long universe, long time, long obj, KMemoryElement payload) {
        return internal_put(universe, time, obj, payload, false);
    }

    private synchronized KMemoryElement internal_put(long universe, long time, long p_obj, KMemoryElement payload, boolean force) {
        int entry = -1;
        int index = -1;
        int hash = (int) (universe ^ time ^ p_obj);
        if (_state.elementDataSize != 0) {
            index = (hash & 0x7FFFFFFF) % _state.elementDataSize;
            entry = findNonNullKeyEntry(universe, time, p_obj, index, _state);
        }
        if (entry == -1) {
            if (++_elementCount > _threshold) {
                rehashCapacity(_state.elementDataSize);
                index = (hash & 0x7FFFFFFF) % _state.elementDataSize;
            }
            int newIndex = (this._elementCount - 1 + this._droppedCount);
            _state.elementK3[(newIndex * 3)] = universe;
            _state.elementK3[((newIndex * 3) + 1)] = time;
            _state.elementK3[((newIndex * 3) + 2)] = p_obj;
            _state.values[newIndex] = payload;
            _state.elementNext[newIndex] = _state.elementHash[index];
            //now the object is reachable to other thread everything should be ready
            _state.elementHash[index] = newIndex;
            return payload;
        } else {
            if (force) {
                _state.values[entry] = payload;/*setValue*/
                return payload;
            } else {
                return _state.values[entry];
            }
        }
    }

    final int findNonNullKeyEntry(long universe, long time, long obj, int index, InternalState internalState) {
        int m = _state.elementHash[index];
        while (m >= 0) {
            if (universe == internalState.elementK3[m * 3] && time == internalState.elementK3[(m * 3) + 1] && obj == internalState.elementK3[(m * 3) + 2]) {
                return m;
            }
            m = _state.elementNext[m];
        }
        return -1;
    }

    @Override
    public KContentKey[] dirtyKeys() {
        int nbDirties = 0;
        InternalState internalState = _state;
        for (int i = 0; i < internalState.elementDataSize; i++) {
            if (internalState.values[i] != null) {
                if (internalState.values[i].isDirty()) {
                    nbDirties++;
                }
            }
        }
        KContentKey[] collectedDirties = new KContentKey[nbDirties];
        nbDirties = 0;
        for (int i = 0; i < internalState.elementDataSize; i++) {
            if (internalState.values[i] != null) {
                if (internalState.values[i].isDirty()) {
                    collectedDirties[nbDirties] = new KContentKey(internalState.elementK3[i * 3], internalState.elementK3[(i * 3) + 1], internalState.elementK3[(i * 3) + 2]);
                    nbDirties++;
                }
            }
        }
        return collectedDirties;
    }

    /**
     * @native ts
     */
    @Override
    public void clean(KMetaModel metaModel) {
        common_clean_monitor(null, metaModel);
    }

    /**
     * @native ts
     */
    @Override
    public void monitor(KObject origin) {
        common_clean_monitor(origin, null);
    }

    @Override
    public final int size() {
        return this._elementCount;
    }

    /**
     * @ignore ts
     */
    private synchronized void common_clean_monitor(KObject origin, KMetaModel p_metaModel) {
        if (origin != null) {
            if (rootReference != null) {
                rootReference.next = new KObjectWeakReference(origin);
            } else {
                rootReference = new KObjectWeakReference(origin);
            }
        } else {
            KObjectWeakReference current = rootReference;
            KObjectWeakReference previous = null;
            while (current != null) {
                //processValues current
                if (current.get() == null) {
                    //check is dirty
                    HeapMemoryChunk currentEntry = (HeapMemoryChunk) this.get(current.universe, current.time, current.uuid);
                    if (currentEntry == null || !currentEntry.isDirty()) {
                        //call the clean sub processValues for universe/time/uuid
                        MemorySegmentResolutionTrace resolved = ResolutionHelper.resolve_trees(current.universe, current.time, current.uuid, this);
                        resolved.getUniverseTree().dec();
                        if (resolved.getUniverseTree().counter() <= 0) {
                            remove(KConfig.NULL_LONG, KConfig.NULL_LONG, current.uuid, p_metaModel);
                        }
                        resolved.getTimeTree().dec();
                        if (resolved.getTimeTree().counter() <= 0) {
                            remove(resolved.getUniverse(), KConfig.NULL_LONG, current.uuid, p_metaModel);
                        }
                        resolved.getSegment().dec();
                        if (resolved.getSegment().counter() <= 0) {
                            remove(resolved.getUniverse(), resolved.getTime(), current.uuid, p_metaModel);
                        }
                        //change chaining
                        if (previous == null) { //first case
                            rootReference = current.next;
                        } else { //in the middle case
                            previous.next = current.next;
                        }
                    }
                }
                previous = current;
                current = current.next;
            }
            //now we try to compact if deleted elements
            compact();
        }
    }

    private void remove(long universe, long time, long obj, KMetaModel p_metaModel) {
        InternalState internalState = _state;
        int hash = (int) (universe ^ time ^ obj);
        int index = (hash & 0x7FFFFFFF) % internalState.elementDataSize;
        if (_state.elementDataSize == 0) {
            return;
        }
        int m = _state.elementHash[index];
        int last = -1;
        while (m >= 0) {
            if (universe == internalState.elementK3[m * 3] && time == internalState.elementK3[(m * 3) + 1] && obj == internalState.elementK3[(m * 3) + 2]) {
                break;
            }
            last = m;
            m = _state.elementNext[m];
        }
        if (m == -1) {
            return;
        }
        if (last == -1) {
            if (_state.elementNext[m] != -1) {
                _state.elementHash[index] = m;
            } else {
                _state.elementHash[index] = -1;
            }
        } else {
            _state.elementNext[last] = _state.elementNext[m];
        }
        _state.elementNext[m] = -1;//flag to dropped value
        _state.values[m].free(p_metaModel);
        _state.values[m] = null;
        this._elementCount--;
        this._droppedCount++;
    }

    private void compact() {
        InternalState internalState = _state;
        if (this._droppedCount > 0) {
            int length = (this._elementCount == 0 ? 1 : this._elementCount << 1); //take the next size of element count
            KMemoryElement[] newValues = new KMemoryElement[length];
            int[] newElementNext = new int[length];
            int[] newElementHash = new int[length];
            long[] newElementKV = new long[length * 3];
            int currentIndex = 0;
            for (int i = 0; i < length; i++) {
                newElementNext[i] = -1;
                newElementHash[i] = -1;
            }
            for (int i = 0; i < internalState.elementDataSize; i++) {
                KMemoryElement loopElement = internalState.values[i];
                if (loopElement != null) {
                    long l_uni = internalState.elementK3[(i * 3)];
                    long l_time = internalState.elementK3[(i * 3) + 1];
                    long l_obj = internalState.elementK3[(i * 3) + 2];

                    newValues[currentIndex] = loopElement;
                    newElementKV[(currentIndex * 3)] = l_uni;
                    newElementKV[(currentIndex * 3) + 1] = l_time;
                    newElementKV[(currentIndex * 3) + 2] = l_obj;

                    int hash = (int) (l_uni ^ l_time ^ l_obj);
                    int index = (hash & 0x7FFFFFFF) % length;
                    newElementNext[currentIndex] = newElementHash[index];
                    newElementHash[index] = currentIndex;
                    currentIndex++;
                }
            }
            _state = new InternalState(length, newElementKV, newElementNext, newElementHash, newValues);
            this._elementCount = currentIndex;
            this._droppedCount = 0;
            this._threshold = (int) (length * this._loadFactor);
        }
    }

    @Override
    public final void clear(KMetaModel metaModel) {
        if (_elementCount > 0) {
            InternalState internalState = _state;
            for (int i = 0; i < internalState.elementDataSize; i++) {
                if (internalState.values[i] != null) {
                    internalState.values[i].free(metaModel);
                }
            }
            int initialCapacity = KConfig.CACHE_INIT_SIZE;
            InternalState newstate = new InternalState(initialCapacity, new long[initialCapacity * 3], new int[initialCapacity], new int[initialCapacity], new KMemoryElement[initialCapacity]);
            for (int i = 0; i < initialCapacity; i++) {
                newstate.elementNext[i] = -1;
                newstate.elementHash[i] = -1;
            }
            this._elementCount = 0;
            this._droppedCount = 0;
            this._state = newstate;
            this._threshold = (int) (newstate.elementDataSize * _loadFactor);
        }
    }

    @Override
    public void delete(KMetaModel metaModel) {
        InternalState internalState = _state;
        _state = null; //this object should not be used anymore
        for (int i = 0; i < internalState.elementDataSize; i++) {
            if (internalState.values[i] != null) {
                internalState.values[i].free(metaModel);
            }
        }
        this._elementCount = 0;
        this._droppedCount = 0;
        this._threshold = 0;
    }


}



