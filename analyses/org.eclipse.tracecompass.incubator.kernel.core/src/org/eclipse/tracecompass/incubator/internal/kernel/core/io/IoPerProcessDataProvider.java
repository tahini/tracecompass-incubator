/*******************************************************************************
 * Copyright (c) 2020 École Polytechnique de Montréal
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License 2.0 which
 * accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/

package org.eclipse.tracecompass.incubator.internal.kernel.core.io;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.tracecompass.analysis.os.linux.core.model.OsStrings;
import org.eclipse.tracecompass.incubator.analysis.core.model.IHostModel;
import org.eclipse.tracecompass.incubator.analysis.core.model.ModelManager;
import org.eclipse.tracecompass.internal.analysis.os.linux.core.inputoutput.Disk;
import org.eclipse.tracecompass.internal.tmf.core.model.TmfXyResponseFactory;
import org.eclipse.tracecompass.internal.tmf.core.model.tree.AbstractTreeDataProvider;
import org.eclipse.tracecompass.statesystem.core.ITmfStateSystem;
import org.eclipse.tracecompass.statesystem.core.exceptions.StateSystemDisposedException;
import org.eclipse.tracecompass.statesystem.core.exceptions.TimeRangeException;
import org.eclipse.tracecompass.statesystem.core.interval.ITmfStateInterval;
import org.eclipse.tracecompass.tmf.core.dataprovider.DataProviderParameterUtils;
import org.eclipse.tracecompass.tmf.core.model.CommonStatusMessage;
import org.eclipse.tracecompass.tmf.core.model.YModel;
import org.eclipse.tracecompass.tmf.core.model.timegraph.IElementResolver;
import org.eclipse.tracecompass.tmf.core.model.tree.ITmfTreeDataModel;
import org.eclipse.tracecompass.tmf.core.model.tree.ITmfTreeDataProvider;
import org.eclipse.tracecompass.tmf.core.model.tree.TmfTreeDataModel;
import org.eclipse.tracecompass.tmf.core.model.tree.TmfTreeModel;
import org.eclipse.tracecompass.tmf.core.model.xy.ITmfTreeXYDataProvider;
import org.eclipse.tracecompass.tmf.core.model.xy.ITmfXyModel;
import org.eclipse.tracecompass.tmf.core.model.xy.IYModel;
import org.eclipse.tracecompass.tmf.core.response.ITmfResponse;
import org.eclipse.tracecompass.tmf.core.response.ITmfResponse.Status;
import org.eclipse.tracecompass.tmf.core.response.TmfModelResponse;
import org.eclipse.tracecompass.tmf.core.trace.ITmfTrace;
import org.eclipse.tracecompass.tmf.core.trace.TmfTraceUtils;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;

/**
 *
 *
 * @author Geneviève Bastien
 */
@SuppressWarnings("restriction")
public class IoPerProcessDataProvider extends AbstractTreeDataProvider<IoAnalysis, TmfTreeDataModel> implements ITmfTreeXYDataProvider<TmfTreeDataModel> {

    /**
     * Provider unique ID.
     */
    public static final String ID = "org.eclipse.tracecompass.incubator.kernel.core.io.per.process"; //$NON-NLS-1$
    private static final String READ_TITLE = "Read";
    private static final String WRITE_TITLE = "Write";

    private static final Comparator<ITmfStateInterval> INTERVAL_COMPARATOR = Comparator.comparing(ITmfStateInterval::getStartTime);

    private final Map<Integer, String> fQuarkToString = new HashMap<>();


    // Data model class that has metadata
    private static final class IoTreeDataModel extends TmfTreeDataModel implements IElementResolver {

        private Multimap<String, Object> fMetadata;

        public IoTreeDataModel(Long id, long parentId, String name, String tid) {
            super(id, parentId, name);
            fMetadata = HashMultimap.create();
            try {
                int intTid = Integer.parseInt(tid);
                fMetadata.put(OsStrings.tid(), intTid);
            } catch (NumberFormatException e) {
                // Nothing to do, can't be parsed to int
            }
        }

        @Override
        public Multimap<String, Object> getMetadata() {
            return fMetadata;
        }

    }

    /**
     * Inline class to encapsulate all the values required to build a series.
     * Allows for reuse of full query results to be faster than {@link Disk}.
     */
    private static final class SeriesBuilder {

        private final long fId;
        /** This series' sector quark. public because final */
        private final int fMainQuark;
        private final double[] fValues;
        private double fPrevCount;
        private List<Integer> fRunning;

        /**
         * Constructor
         *
         * @param name
         *            the series name
         * @param sectorQuark
         *            sector quark
         * @param list
         * @param length
         *            desired length of the series
         */
        private SeriesBuilder(long id, int sectorQuark, List<Integer> list, int length) {
            fId = id;
            fMainQuark = sectorQuark;
            fRunning = list;
            fValues = new double[length];
            fPrevCount = -1;
        }

        private List<Integer> getQuarks() {
            List<Integer> quarks = new ArrayList<>(fRunning);
            quarks.add(fMainQuark);
            return quarks;
        }

        /**
         * Update the value for the counter at the desired index. Use in
         * increasing order of position
         *
         * @param intervals
         *            index to update
         * @param currentIndex
         * @param newCount
         *            new number of read / written sectors
         * @param deltaT
         *            time difference to the previous value for interpolation
         */
        private void updateValue(Map<Integer, Set<ITmfStateInterval>> intervals, long time, long prevTime, int currentIndex) {
            long deltaT = time - prevTime;
            // Same value asked twice, it should be the same as previous count
            if (deltaT == 0) {
                if (currentIndex > 0) {
                    fValues[currentIndex] = fValues[currentIndex - 1];
                }
                return;
            }

            // Get the value at time
            double valueAtTime = getValueAtTime(intervals.get(fMainQuark), time);

            // Interpolate the values of the running requests
            for (Integer quark : fRunning) {
                valueAtTime += interpolateAtTime(intervals.get(quark), time);
            }

            if (prevTime == -1) {
                fPrevCount = valueAtTime;
                return;
            }

            // Compute the new value
            fValues[currentIndex] = (valueAtTime - fPrevCount) / deltaT;
            fPrevCount = valueAtTime;
        }

        private static double interpolateAtTime(@Nullable Set<ITmfStateInterval> intervals, long time) {
            if (intervals == null) {
                return 0.0;
            }
            ITmfStateInterval interval = findInterval(intervals, time);
            if (interval == null) {
                return 0.0;
            }
            Object value = interval.getValue();
            return (value instanceof Number) ? interpolate(time, interval, ((Number) value)) : 0.0;
        }

        private static double interpolate(long time, ITmfStateInterval interval, Number size) {
            long runningTime = interval.getEndTime() - interval.getStartTime() + 1;
            return (time - interval.getStartTime()) * size.doubleValue() / runningTime;
        }

        private static double getValueAtTime(@Nullable Set<ITmfStateInterval> intervals, long time) {
            if (intervals == null) {
                return 0.0;
            }
            ITmfStateInterval interval = findInterval(intervals, time);
            if (interval == null) {
                return 0.0;
            }
            Object value = interval.getValue();
            return (value instanceof Number) ? ((Number) value).doubleValue() : 0.0;
        }

        private @Nullable
        static ITmfStateInterval findInterval(Set<ITmfStateInterval> intervals, long time) {
            for (ITmfStateInterval interval : intervals) {
                // Intervals are sorted, return if interval has start time later than time, the interval is not there
                if (interval.getStartTime() > time) {
                    return null;
                }
                if (time >= interval.getStartTime() && time <= interval.getEndTime()) {
                    return interval;
                }
            }
            return null;
        }

        private IYModel build() {
            return new YModel(fId, String.valueOf(fId), fValues);
        }
    }

    /**
     * Constructor
     *
     * @param trace
     *            The trace this data provider is for
     * @param analysisModule
     *            The analysis module
     */
    public IoPerProcessDataProvider(ITmfTrace trace, IoAnalysis analysisModule) {
        super(trace, analysisModule);
        analysisModule.schedule();
    }

    /**
     * Create the time graph data provider
     *
     * @param trace
     *            The trace for which is the data provider
     * @return The data provider
     */
    public static @Nullable ITmfTreeDataProvider<? extends ITmfTreeDataModel> create(ITmfTrace trace) {
        IoAnalysis module = TmfTraceUtils.getAnalysisModuleOfClass(trace, IoAnalysis.class, IoAnalysis.ID);
        return module != null ? new IoPerProcessDataProvider(trace, module) : null;
    }


    @Override
    public String getId() {
        return ID;
    }

    @Override
    protected boolean isCacheable() {
        return true;
    }

    @Override
    protected TmfTreeModel<TmfTreeDataModel> getTree(ITmfStateSystem ss, Map<String, Object> fetchParameters, @Nullable IProgressMonitor monitor) throws StateSystemDisposedException {
        // Make an entry for each base quark
        List<TmfTreeDataModel> entryList = new ArrayList<>();
        String traceName = Objects.requireNonNull(getTrace().getName());
        long rootId = getId(ITmfStateSystem.ROOT_ATTRIBUTE);
        entryList.add(new TmfTreeDataModel(rootId, -1, Collections.singletonList(traceName)));
        for (Integer quark : ss.getQuarks(IoStateProvider.ATTRIBUTE_TID, "*")) { //$NON-NLS-1$
            int readQuark = ss.optQuarkRelative(quark, IoStateProvider.ATTRIBUTE_READ);
            int writeQuark = ss.optQuarkRelative(quark, IoStateProvider.ATTRIBUTE_WRITE);
            if (readQuark == ITmfStateSystem.INVALID_ATTRIBUTE && writeQuark == ITmfStateSystem.INVALID_ATTRIBUTE) {
                // Do not display threads that have no read/write
                continue;
            }

            String tid = ss.getAttributeName(quark);
            String tidName = resolveThreadName(tid, ss.getCurrentEndTime());
            Long id = getId(quark);
            entryList.add(new IoTreeDataModel(id, rootId, tidName, tid));
            if (readQuark != ITmfStateSystem.INVALID_ATTRIBUTE) {
                Long readId = getId(readQuark);
                entryList.add(new IoTreeDataModel(readId, id, READ_TITLE, tid));
                fQuarkToString.put(readQuark, traceName + '/' + tidName + '/' + READ_TITLE);
            }
            if (writeQuark != ITmfStateSystem.INVALID_ATTRIBUTE) {
                Long writeId = getId(writeQuark);
                entryList.add(new IoTreeDataModel(writeId, id, WRITE_TITLE, tid));
                fQuarkToString.put(writeQuark, traceName + '/' + tidName + '/' + WRITE_TITLE);
            }
        }
        return new TmfTreeModel<>(Collections.emptyList(), entryList);
    }

    private @Nullable String resolveThreadName(String tidStr, long time) {
        try {
            int tid = Integer.parseInt(tidStr);
            IHostModel model = ModelManager.getModelFor(getTrace().getHostId());
            String tname = model.getExecName(tid, time);
            return tname == null ? tidStr : tname + ' ' + '(' + tidStr + ')';
        } catch (NumberFormatException e) {
            return tidStr;
        }

    }

    @Override
    public TmfModelResponse<ITmfXyModel> fetchXY(Map<String, Object> fetchParameters, @Nullable IProgressMonitor monitor) {
        ITmfStateSystem ss = getAnalysisModule().getStateSystem();
        if (ss == null) {
            return new TmfModelResponse<>(null, ITmfResponse.Status.FAILED, CommonStatusMessage.ANALYSIS_INITIALIZATION_FAILED);
        }

        List<SeriesBuilder> builders = new ArrayList<>();
        // Prepare the quarks to display
        Collection<Long> selectedItems = DataProviderParameterUtils.extractSelectedItems(fetchParameters);
        Map<Long, Integer> selectedEntries = getSelectedEntries(selectedItems);
        List<Long> times = getTimes(ss, DataProviderParameterUtils.extractTimeRequested(fetchParameters));
        List<Integer> quarksToQuery = new ArrayList<>();
        long currentEnd = ss.getCurrentEndTime();
        boolean complete = ss.waitUntilBuilt(0) || times.get(times.size() - 1) <= currentEnd;

        for (Entry<Long, Integer> entry : selectedEntries.entrySet()) {
            // Add only quarks that can be displayed, ie, those in the fQuarkToString map
            if (fQuarkToString.containsKey(entry.getValue())) {
                SeriesBuilder seriesBuilder = new SeriesBuilder(entry.getKey(), entry.getValue(), ss.getSubAttributes(entry.getValue(), false), times.size());
                builders.add(seriesBuilder);
                quarksToQuery.addAll(seriesBuilder.getQuarks());
            }
        }
        long[] nativeTimes = new long[times.size()];
        for (int i = 0; i < times.size(); i++) {
            nativeTimes[i] = times.get(i);
        }

        // Put all intervals in a map, there shouldn't be too many, we'll handle them later
        Map<Integer, Set<ITmfStateInterval>> intervals = new HashMap<>();
        try {
            for (ITmfStateInterval interval : ss.query2D(quarksToQuery, times)) {
                if (monitor != null && monitor.isCanceled()) {
                    return new TmfModelResponse<>(null, Status.CANCELLED, CommonStatusMessage.TASK_CANCELLED);
                }
                intervals.computeIfAbsent(interval.getAttribute(), q -> new TreeSet<>(INTERVAL_COMPARATOR)).add(interval);
            }
        } catch (IndexOutOfBoundsException | TimeRangeException | StateSystemDisposedException e) {
            return new TmfModelResponse<>(null, Status.FAILED, CommonStatusMessage.STATE_SYSTEM_FAILED);
        }

        // Get the values for each time
        long prevTime = -1;
        for (int i = 0; i < times.size(); i++) {
            if (monitor != null && monitor.isCanceled()) {
                return new TmfModelResponse<>(null, Status.CANCELLED, CommonStatusMessage.TASK_CANCELLED);
            }
            long time = nativeTimes[i];

            if (time > currentEnd) {
                break;
            } else if (time >= ss.getStartTime()) {

                for (SeriesBuilder builder : builders) {
                    builder.updateValue(intervals, time, prevTime, i);
                }
            }
            prevTime = time;

        }
        List<IYModel> models = new ArrayList<>();
        for (SeriesBuilder builder : builders) {
            models.add(builder.build());
        }

        return TmfXyResponseFactory.create("Example XY data provider", nativeTimes, models, complete); //$NON-NLS-1$
    }

    private static List<Long> getTimes(ITmfStateSystem key, @Nullable List<Long> list) {
        if (list == null) {
            return Collections.emptyList();
        }
        List<@NonNull Long> times = new ArrayList<>();
        for (long t : list) {
            if (key.getStartTime() <= t && t <= key.getCurrentEndTime()) {
                times.add(t);
            }
        }
        Collections.sort(times);
        return times;
    }

}
