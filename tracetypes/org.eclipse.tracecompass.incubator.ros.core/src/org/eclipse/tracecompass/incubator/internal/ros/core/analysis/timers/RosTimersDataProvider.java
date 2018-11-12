/**********************************************************************
 * Copyright (c) 2018 Ericsson, École Polytechnique de Montréal
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 **********************************************************************/

package org.eclipse.tracecompass.incubator.internal.ros.core.analysis.timers;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.tracecompass.incubator.internal.ros.core.analysis.ElementReferenceState;
import org.eclipse.tracecompass.incubator.internal.ros.core.analysis.PeriodState;
import org.eclipse.tracecompass.internal.tmf.core.model.filters.FetchParametersUtils;
import org.eclipse.tracecompass.internal.tmf.core.model.filters.TimeGraphStateQueryFilter;
import org.eclipse.tracecompass.internal.tmf.core.model.timegraph.AbstractTimeGraphDataProvider;
import org.eclipse.tracecompass.statesystem.core.ITmfStateSystem;
import org.eclipse.tracecompass.statesystem.core.exceptions.StateSystemDisposedException;
import org.eclipse.tracecompass.statesystem.core.interval.ITmfStateInterval;
import org.eclipse.tracecompass.tmf.core.model.CommonStatusMessage;
import org.eclipse.tracecompass.tmf.core.model.filters.SelectionTimeQueryFilter;
import org.eclipse.tracecompass.tmf.core.model.filters.TimeQueryFilter;
import org.eclipse.tracecompass.tmf.core.model.timegraph.ITimeGraphArrow;
import org.eclipse.tracecompass.tmf.core.model.timegraph.ITimeGraphRowModel;
import org.eclipse.tracecompass.tmf.core.model.timegraph.ITimeGraphState;
import org.eclipse.tracecompass.tmf.core.model.timegraph.TimeGraphEntryModel;
import org.eclipse.tracecompass.tmf.core.model.timegraph.TimeGraphModel;
import org.eclipse.tracecompass.tmf.core.model.timegraph.TimeGraphRowModel;
import org.eclipse.tracecompass.tmf.core.model.timegraph.TimeGraphState;
import org.eclipse.tracecompass.tmf.core.model.tree.TmfTreeModel;
import org.eclipse.tracecompass.tmf.core.response.ITmfResponse;
import org.eclipse.tracecompass.tmf.core.response.TmfModelResponse;
import org.eclipse.tracecompass.tmf.core.trace.ITmfTrace;

import com.google.common.collect.ImmutableList.Builder;
import com.google.common.collect.TreeMultimap;

/**
 * Data provider for the ROS Timers view
 *
 * @author Christophe Bedard
 */
@SuppressWarnings("restriction")
public class RosTimersDataProvider extends AbstractTimeGraphDataProvider<@NonNull RosTimersAnalysis, @NonNull TimeGraphEntryModel> {

    /** Data provider suffix ID */
    public static final String SUFFIX = ".dataprovider"; //$NON-NLS-1$

    /**
     * Constructor
     *
     * @param trace
     *            the trace for this provider
     * @param analysisModule
     *            the corresponding analysis module
     */
    public RosTimersDataProvider(@NonNull ITmfTrace trace, @NonNull RosTimersAnalysis analysisModule) {
        super(trace, analysisModule);
    }

    @Deprecated
    @Override
    public @NonNull TmfModelResponse<@NonNull List<@NonNull ITimeGraphArrow>> fetchArrows(@NonNull TimeQueryFilter filter, @Nullable IProgressMonitor monitor) {
        Map<String, Object> parameters = FetchParametersUtils.timeQueryToMap(filter);
        return fetchArrows(parameters, monitor);
    }

    @Deprecated
    @Override
    public @NonNull TmfModelResponse<@NonNull Map<@NonNull String, @NonNull String>> fetchTooltip(@NonNull SelectionTimeQueryFilter filter, @Nullable IProgressMonitor monitor) {
        Map<String, Object> parameters = FetchParametersUtils.selectionTimeQueryToMap(filter);
        return fetchTooltip(parameters, monitor);
    }

    @Override
    public @NonNull TmfModelResponse<@NonNull List<@NonNull ITimeGraphArrow>> fetchArrows(@NonNull Map<@NonNull String, @NonNull Object> fetchParameters, @Nullable IProgressMonitor monitor) {
        return new TmfModelResponse<>(null, ITmfResponse.Status.COMPLETED, CommonStatusMessage.COMPLETED);
    }

    @Override
    public @NonNull TmfModelResponse<@NonNull Map<@NonNull String, @NonNull String>> fetchTooltip(@NonNull Map<@NonNull String, @NonNull Object> fetchParameters, @Nullable IProgressMonitor monitor) {
        return new TmfModelResponse<>(null, ITmfResponse.Status.COMPLETED, CommonStatusMessage.COMPLETED);
    }

    @Override
    public @NonNull String getId() {
        return getAnalysisModule().getId() + SUFFIX;
    }

    @Override
    protected @Nullable TimeGraphModel getRowModel(@NonNull ITmfStateSystem ss, @NonNull Map<@NonNull String, @NonNull Object> parameters, @Nullable IProgressMonitor monitor) throws StateSystemDisposedException {
        TreeMultimap<Integer, ITmfStateInterval> intervals = TreeMultimap.create(Comparator.naturalOrder(),
                Comparator.comparing(ITmfStateInterval::getStartTime));
        SelectionTimeQueryFilter filter = FetchParametersUtils.createSelectionTimeQuery(parameters);
        if (filter == null) {
            return null;
        }
        Map<@NonNull Long, @NonNull Integer> entries = getSelectedEntries(filter);
        Collection<Long> times = getTimes(filter, ss.getStartTime(), ss.getCurrentEndTime());

        // Query
        for (ITmfStateInterval interval : ss.query2D(entries.values(), times)) {
            if (monitor != null && monitor.isCanceled()) {
                return new TimeGraphModel(Collections.emptyList());
            }
            intervals.put(interval.getAttribute(), interval);
        }

        Map<@NonNull Integer, @NonNull Predicate<@NonNull Map<@NonNull String, @NonNull String>>> predicates = new HashMap<>();
        if (filter instanceof TimeGraphStateQueryFilter) {
            TimeGraphStateQueryFilter timeEventFilter = (TimeGraphStateQueryFilter) filter;
            predicates.putAll(computeRegexPredicate(timeEventFilter));
        }

        List<@NonNull ITimeGraphRowModel> rows = new ArrayList<>();
        for (Map.Entry<@NonNull Long, @NonNull Integer> entry : entries.entrySet()) {
            if (monitor != null && monitor.isCanceled()) {
                return new TimeGraphModel(Collections.emptyList());
            }

            List<ITimeGraphState> eventList = new ArrayList<>();
            for (ITmfStateInterval interval : intervals.get(entry.getValue())) {
                long startTime = interval.getStartTime();
                long duration = interval.getEndTime() - startTime + 1;
                Object valObject = interval.getValue();
                if (valObject instanceof Long) {
                    String attributeName = ss.getAttributeName(interval.getAttribute());
                    if (attributeName.startsWith(ElementReferenceState.HEX_PREFIX)) {
                        // Timer period associated with timer callback ref
                        long ref = (Long) valObject;
                        PeriodState state = new PeriodState(startTime, duration, ref);
                        addToStateList(eventList, state, entry.getKey(), predicates, monitor);
                    } else {
                        // Queue reference
                        long ref = (Long) valObject;
                        ElementReferenceState state = new ElementReferenceState(startTime, duration, ref);
                        addToStateList(eventList, state, entry.getKey(), predicates, monitor);
                    }
                } else if (valObject instanceof Integer) {
                    // Count/index
                    int count = (Integer) valObject;
                    TimeGraphState state = new TimeGraphState(startTime, duration, 0, Integer.toString(count));
                    addToStateList(eventList, state, entry.getKey(), predicates, monitor);
                }
            }
            rows.add(new TimeGraphRowModel(entry.getKey(), eventList));

        }
        return new TimeGraphModel(rows);
    }

    @Override
    protected boolean isCacheable() {
        return false;
    }

    @Override
    protected @NonNull TmfTreeModel<@NonNull TimeGraphEntryModel> getTree(@NonNull ITmfStateSystem ss, @NonNull Map<@NonNull String, @NonNull Object> parameters, @Nullable IProgressMonitor monitor) throws StateSystemDisposedException {
        Builder<@NonNull TimeGraphEntryModel> builder = new Builder<>();
        long parentId = getId(ITmfStateSystem.ROOT_ATTRIBUTE);
        builder.add(new TimeGraphEntryModel(parentId, -1, String.valueOf(getTrace().getName()), ss.getStartTime(), ss.getCurrentEndTime()));
        addChildren(ss, builder, ITmfStateSystem.ROOT_ATTRIBUTE, parentId);
        return new TmfTreeModel<>(Collections.emptyList(), builder.build());
    }

    private void addChildren(ITmfStateSystem ss, Builder<@NonNull TimeGraphEntryModel> builder, int quark, long parentId) {
        for (Integer child : ss.getSubAttributes(quark, false)) {
            long childId = getId(child);
            String name = ss.getAttributeName(child);
            boolean isRowModel = quark != ITmfStateSystem.ROOT_ATTRIBUTE;
            builder.add(new TimeGraphEntryModel(childId, parentId, name, ss.getStartTime(), ss.getCurrentEndTime(), isRowModel));
            addChildren(ss, builder, child, childId);
        }
    }

    /**
     * @return the full dataprovider ID
     */
    public static String getFullDataProviderId() {
        return RosTimersAnalysis.getFullAnalysisId() + RosTimersDataProvider.SUFFIX;
    }
}
