/*******************************************************************************
 * Copyright (c) 2017 École Polytechnique de Montréal
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.eclipse.tracecompass.incubator.internal.perf.profiling.core.callgraph;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.tracecompass.incubator.callstack.core.callstack.ICallStackGroupDescriptor;
import org.eclipse.tracecompass.incubator.callstack.core.callstack.IEventCallStackProvider;
import org.eclipse.tracecompass.incubator.internal.callstack.core.callgraph.CallGraphGroupBy;
import org.eclipse.tracecompass.incubator.internal.callstack.core.callgraph.GroupNode;
import org.eclipse.tracecompass.incubator.internal.callstack.core.callgraph.ICallGraphProvider;
import org.eclipse.tracecompass.incubator.internal.callstack.core.callgraph.profiling.ProfilingGroup;
import org.eclipse.tracecompass.incubator.internal.callstack.core.callgraph.profiling.ProfilingGroupDescriptor;
import org.eclipse.tracecompass.incubator.internal.callstack.core.callgraph.profiling.SampledCallGraphFactory;
import org.eclipse.tracecompass.incubator.internal.perf.profiling.core.Activator;
import org.eclipse.tracecompass.tmf.core.analysis.TmfAbstractAnalysisModule;
import org.eclipse.tracecompass.tmf.core.event.ITmfEvent;
import org.eclipse.tracecompass.tmf.core.event.ITmfEventField;
import org.eclipse.tracecompass.tmf.core.event.TmfEvent;
import org.eclipse.tracecompass.tmf.core.exceptions.TmfAnalysisException;
import org.eclipse.tracecompass.tmf.core.request.ITmfEventRequest;
import org.eclipse.tracecompass.tmf.core.request.TmfEventRequest;
import org.eclipse.tracecompass.tmf.core.timestamp.TmfTimeRange;
import org.eclipse.tracecompass.tmf.core.trace.ITmfTrace;
import org.eclipse.tracecompass.tmf.core.trace.experiment.TmfExperiment;

import com.google.common.collect.ImmutableList;

public class PerfCallchainAnalysisModule extends TmfAbstractAnalysisModule implements ICallGraphProvider, IEventCallStackProvider {

    private ITmfEventRequest fRequest;
    private final Map<Long, GroupNode> fProcessGroups = new HashMap<>();
    private final Map<Long, ProfilingGroup> fThreadGroups = new HashMap<>();
    private final ICallStackGroupDescriptor fProcessDescriptor;
    private final ICallStackGroupDescriptor fThreadDescriptor;
    private @Nullable ICallStackGroupDescriptor fGroupBy;

//    private final ProfilingGroup fGroupNode = new ProfilingGroup("Data", CallGraphAllGroupDescriptor.getInstance());

    public PerfCallchainAnalysisModule() {
        fThreadDescriptor = new ProfilingGroupDescriptor("Threads", null);
        fProcessDescriptor = new ProfilingGroupDescriptor("Process", fThreadDescriptor);
    }

    @Override
    protected boolean executeAnalysis(@NonNull IProgressMonitor monitor) throws TmfAnalysisException {
        ITmfTrace trace = getTrace();
        if (trace == null) {
            throw new IllegalStateException("Trace has not been set, yet the analysis is being run!");
        }
        /* Cancel any previous request */
        ITmfEventRequest request = fRequest;
        if ((request != null) && (!request.isCompleted())) {
            request.cancel();
        }

        try {
            request = new PerfCallchainEventRequest(trace);
            fRequest = request;
            trace.sendRequest(request);

            request.waitForCompletion();
        } catch (InterruptedException e) {
            Activator.getInstance().logError("Request interrupted", e); //$NON-NLS-1$
        }
        return request.isCompleted();
    }

    @Override
    protected void canceling() {
        ITmfEventRequest req = fRequest;
        if ((req != null) && (!req.isCompleted())) {
            req.cancel();
        }
    }

    private class PerfCallchainEventRequest extends TmfEventRequest {
        private final ITmfTrace fTrace;

        /**
         * Constructor
         * @param trace The trace
         */
        public PerfCallchainEventRequest(ITmfTrace trace) {
            super(TmfEvent.class,
                    TmfTimeRange.ETERNITY,
                    0,
                    ITmfEventRequest.ALL_DATA,
                    ITmfEventRequest.ExecutionType.BACKGROUND);
            fTrace = trace;
        }

        @Override
        public void handleData(final ITmfEvent event) {
            super.handleData(event);
            if (event.getTrace() == fTrace) {
                processEvent(event);
            } else if (fTrace instanceof TmfExperiment) {
                /*
                 * If the request is for an experiment, check if the event is
                 * from one of the child trace
                 */
                for (ITmfTrace childTrace : ((TmfExperiment) fTrace).getTraces()) {
                    if (childTrace == event.getTrace()) {
                        processEvent(event);
                    }
                }
            }
        }

        private void processEvent(ITmfEvent event) {
            if (!event.getName().equals("cycles")) {
                return;
            }
            // Get the callchain is available
            ITmfEventField field = event.getContent().getField("perf_callchain");
            if (field == null) {
                return;
            }
            long[] value = (long[]) field.getValue();
            int size = value.length;
            long tmp;
            for (int i = 0, mid = size >> 1, j = size - 1; i < mid; i++, j--) {
                tmp = value[i];
                value[i] = value[j];
                value[j] = tmp;
            }
            ProfilingGroup lgn = getLeafGroup(event);
            lgn.addStackTrace(value);
        }

        /**
         * @param event
         */
        private ProfilingGroup getLeafGroup(ITmfEvent event) {
            Long fieldValue = event.getContent().getFieldValue(Long.class, "perf_tid");
            Long tid = fieldValue == null ? -1 : fieldValue;
            ProfilingGroup threadGroup = fThreadGroups.get(tid);
            if (threadGroup != null) {
                return threadGroup;
            }
            fieldValue = event.getContent().getFieldValue(Long.class, "perf_pid");
            Long pid = fieldValue == null ? -1 : fieldValue;
            GroupNode processGroup = fProcessGroups.get(pid);
            if (processGroup == null) {
                processGroup = new GroupNode(String.valueOf(pid), fThreadDescriptor);
                fProcessGroups.put(pid, processGroup);
            }
            threadGroup = new ProfilingGroup(String.valueOf(tid), fThreadDescriptor);
            processGroup.addChild(threadGroup);
            fThreadGroups.put(tid, threadGroup);
            // TODO: see if we can add a group
            return threadGroup;
        }
    }

    @Override
    public Collection<GroupNode> getGroups() {
        ICallStackGroupDescriptor groupBy = fGroupBy;
        Collection<GroupNode> groups = fProcessGroups.values();
        // Fast return: return all groups
        if (groupBy == null) {
            return ImmutableList.copyOf(groups);
        }

        return CallGraphGroupBy.groupCallGraphBy(groupBy, groups, SampledCallGraphFactory.getInstance());
    }

    @Override
    public Collection<ICallStackGroupDescriptor> getGroupDescriptor() {
        return ImmutableList.of(fProcessDescriptor, fThreadDescriptor);
    }

    @Override
    public void setGroupBy(@Nullable ICallStackGroupDescriptor descriptor) {
        fGroupBy = descriptor;
    }

    @Override
    public Map<String, Collection<Object>> getCallStack(@NonNull ITmfEvent event) {
        ITmfEventField field = event.getContent().getField("perf_callchain");
        if (field == null) {
            return new HashMap<>();
        }
        return new HashMap<>();
    }

}
