/*******************************************************************************
 * Copyright (c) 2016 Ericsson
 *
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Author:
 *     Sonia Farrah
 *******************************************************************************/
package org.eclipse.tracecompass.incubator.internal.callstack.ui.flamegraph;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.ActionContributionItem;
import org.eclipse.jface.action.GroupMarker;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.action.IMenuCreator;
import org.eclipse.jface.action.IMenuListener;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.IDialogSettings;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.MenuDetectEvent;
import org.eclipse.swt.events.MenuDetectListener;
import org.eclipse.swt.events.MouseAdapter;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.PaintEvent;
import org.eclipse.swt.events.PaintListener;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.tracecompass.common.core.NonNullUtils;
import org.eclipse.tracecompass.common.core.log.TraceCompassLogUtils.FlowScopeLog;
import org.eclipse.tracecompass.common.core.log.TraceCompassLogUtils.FlowScopeLogBuilder;
import org.eclipse.tracecompass.incubator.callstack.core.base.ICallStackGroupDescriptor;
import org.eclipse.tracecompass.incubator.callstack.core.callgraph.AllGroupDescriptor;
import org.eclipse.tracecompass.incubator.callstack.core.callgraph.ICallGraphProvider;
import org.eclipse.tracecompass.incubator.internal.callstack.core.flamegraph.FlameGraphDataProvider;
import org.eclipse.tracecompass.incubator.internal.callstack.ui.Activator;
import org.eclipse.tracecompass.internal.tmf.core.model.filters.FetchParametersUtils;
import org.eclipse.tracecompass.statesystem.core.StateSystemUtils;
import org.eclipse.tracecompass.tmf.core.analysis.IAnalysisModule;
import org.eclipse.tracecompass.tmf.core.dataprovider.DataProviderManager;
import org.eclipse.tracecompass.tmf.core.dataprovider.DataProviderParameterUtils;
import org.eclipse.tracecompass.tmf.core.model.filters.SelectionTimeQueryFilter;
import org.eclipse.tracecompass.tmf.core.model.timegraph.ITimeGraphDataProvider;
import org.eclipse.tracecompass.tmf.core.model.timegraph.ITimeGraphRowModel;
import org.eclipse.tracecompass.tmf.core.model.timegraph.ITimeGraphState;
import org.eclipse.tracecompass.tmf.core.model.timegraph.TimeGraphEntryModel;
import org.eclipse.tracecompass.tmf.core.model.timegraph.TimeGraphModel;
import org.eclipse.tracecompass.tmf.core.model.timegraph.TimeGraphState;
import org.eclipse.tracecompass.tmf.core.model.tree.TmfTreeModel;
import org.eclipse.tracecompass.tmf.core.response.ITmfResponse;
import org.eclipse.tracecompass.tmf.core.response.TmfModelResponse;
import org.eclipse.tracecompass.tmf.core.signal.TmfSignalHandler;
import org.eclipse.tracecompass.tmf.core.signal.TmfSignalManager;
import org.eclipse.tracecompass.tmf.core.signal.TmfTraceClosedSignal;
import org.eclipse.tracecompass.tmf.core.signal.TmfTraceSelectedSignal;
import org.eclipse.tracecompass.tmf.core.symbols.ISymbolProvider;
import org.eclipse.tracecompass.tmf.core.symbols.SymbolProviderManager;
import org.eclipse.tracecompass.tmf.core.timestamp.ITmfTimestamp;
import org.eclipse.tracecompass.tmf.core.trace.ITmfTrace;
import org.eclipse.tracecompass.tmf.core.trace.TmfTraceManager;
import org.eclipse.tracecompass.tmf.core.trace.TmfTraceUtils;
import org.eclipse.tracecompass.tmf.ui.TmfUiRefreshHandler;
import org.eclipse.tracecompass.tmf.ui.editors.ITmfTraceEditor;
import org.eclipse.tracecompass.tmf.ui.symbols.ISymbolProviderPreferencePage;
import org.eclipse.tracecompass.tmf.ui.symbols.SymbolProviderConfigDialog;
import org.eclipse.tracecompass.tmf.ui.symbols.TmfSymbolProviderUpdatedSignal;
import org.eclipse.tracecompass.tmf.ui.views.SaveImageUtil;
import org.eclipse.tracecompass.tmf.ui.views.TmfView;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.TimeGraphPresentationProvider;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.TimeGraphViewer;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.model.ITimeEvent;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.model.ITimeGraphEntry;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.model.NamedTimeEvent;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.model.NullTimeEvent;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.model.TimeEvent;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.model.TimeGraphEntry;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.model.TimeGraphEntry.Sampling;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.widgets.TimeGraphControl;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.widgets.Utils.TimeFormat;
import org.eclipse.ui.IActionBars;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IWorkbenchActionConstants;
import org.eclipse.ui.IWorkbenchPartSite;
import org.eclipse.ui.progress.IWorkbenchSiteProgressService;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Multimap;

/**
 * View to display the flame graph .This uses the flameGraphNode tree generated
 * by CallGraphAnalysisUI.
 *
 * @author Sonia Farrah
 */
@NonNullByDefault({})
public class FlameGraphView extends TmfView {

    /**
     * ID of the view
     */
    public static final String ID = FlameGraphView.class.getPackage().getName() + ".flamegraphView"; //$NON-NLS-1$

    private static final String SYMBOL_MAPPING_ICON_PATH = "icons/obj16/binaries_obj.gif"; //$NON-NLS-1$
    private static final @NonNull String GROUP_BY_ICON_PATH = "icons/etool16/group_by.gif"; //$NON-NLS-1$

    private static final String SORT_OPTION_KEY = "sort.option"; //$NON-NLS-1$
    private static final ImageDescriptor SORT_BY_NAME_ICON = Activator.getDefault().getImageDescripterFromPath("icons/etool16/sort_alpha.gif"); //$NON-NLS-1$
    private static final ImageDescriptor SORT_BY_NAME_REV_ICON = Activator.getDefault().getImageDescripterFromPath("icons/etool16/sort_alpha_rev.gif"); //$NON-NLS-1$
    private static final ImageDescriptor SORT_BY_ID_ICON = Activator.getDefault().getImageDescripterFromPath("icons/etool16/sort_num.gif"); //$NON-NLS-1$
    private static final ImageDescriptor SORT_BY_ID_REV_ICON = Activator.getDefault().getImageDescripterFromPath("icons/etool16/sort_num_rev.gif"); //$NON-NLS-1$
    private static final ImageDescriptor AGGREGATE_BY_ICON = Activator.getDefault().getImageDescripterFromPath(GROUP_BY_ICON_PATH);

    private static final int DEFAULT_BUFFER_SIZE = 3;

    private TimeGraphViewer fTimeGraphViewer;

    private SortOption fSortOption;

    private TimeGraphPresentationProvider fPresentationProvider;

    private ITmfTrace fTrace;
    private static final @NonNull Logger LOGGER = Logger.getLogger(FlameGraphView.class.getName());

    private final @NonNull MenuManager fEventMenuManager = new MenuManager();
    private Action fAggregateByAction;
    private Action fSortByNameAction;
    private Action fSortByIdAction;
    // The action to import a binary file mapping */
    private Action fConfigureSymbolsAction;

    private @Nullable ICallStackGroupDescriptor fGroupBy = null;
    /**
     * A plain old semaphore is used since different threads will be competing
     * for the same resource.
     */
    private final Semaphore fLock = new Semaphore(1);

    // Variable used to specify when the graph is dirty, ie waiting for data refresh
    private final AtomicInteger fDirty = new AtomicInteger();

    /** The trace to build thread hash map */
    private final Map<ITmfTrace, Job> fBuildJobMap = new HashMap<>();
    private final Map<ITimeGraphDataProvider<? extends @NonNull TimeGraphEntryModel>, Map<Long, @NonNull TimeGraphEntry>> fEntries = new HashMap<>();

    /**
     * Set of visible entries to zoom on.
     */
    private @NonNull Set<@NonNull TimeGraphEntry> fVisibleEntries = Collections.emptySet();

    private long fEndTime = Long.MIN_VALUE;

    /** The trace to entry list hash map */
    private final Map<ITmfTrace, List<@NonNull TimeGraphEntry>> fEntryListMap = new HashMap<>();

    private int fDisplayWidth;
    private Comparator<ITimeGraphEntry> fEntryComparator = new ThreadNameComparator();
    private @Nullable ZoomThread fZoomThread;
    private final Object fZoomThreadResultLock = new Object();


    /**
     * Constructor
     */
    public FlameGraphView() {
        super(ID);
    }

    /**
     * Constructor with ID
     *
     * @param id
     *            The ID of the view
     */
    protected FlameGraphView(String id) {
        super(id);
    }

    @Override
    public void createPartControl(Composite parent) {
        super.createPartControl(parent);
        fDisplayWidth = Display.getDefault().getBounds().width;
        fTimeGraphViewer = new TimeGraphViewer(parent, SWT.NONE);
        fPresentationProvider = new FlameGraphPresentationProvider();
        fTimeGraphViewer.setTimeGraphProvider(fPresentationProvider);
        fTimeGraphViewer.setTimeFormat(TimeFormat.NUMBER);
        IEditorPart editor = getSite().getPage().getActiveEditor();
        if (editor instanceof ITmfTraceEditor) {
            ITmfTrace trace = ((ITmfTraceEditor) editor).getTrace();
            if (trace != null) {
                traceSelected(new TmfTraceSelectedSignal(this, trace));
            }
        }
        contributeToActionBars();
        loadSortOption();
        TmfSignalManager.register(this);
        getSite().setSelectionProvider(fTimeGraphViewer.getSelectionProvider());
        createTimeEventContextMenu();
        fTimeGraphViewer.getTimeGraphControl().addMouseListener(new MouseAdapter() {
            @Override
            public void mouseDoubleClick(MouseEvent e) {
                TimeGraphControl timeGraphControl = getTimeGraphViewer().getTimeGraphControl();
                ISelection selection = timeGraphControl.getSelection();
                if (selection instanceof IStructuredSelection) {
                    for (Object object : ((IStructuredSelection) selection).toList()) {
                        if (object instanceof TimeEvent) {
                            TimeEvent event = (TimeEvent) object;
                            long startTime = event.getTime();
                            long endTime = startTime + event.getDuration();
                            getTimeGraphViewer().setStartFinishTime(startTime, endTime);
                            break;
                        }
                    }
                }
            }
        });
        fTimeGraphViewer.addRangeListener(event -> {
            startZoomThread(event.getStartTime(), event.getEndTime(), false);
        });
        TimeGraphControl timeGraphControl = fTimeGraphViewer.getTimeGraphControl();
        timeGraphControl.addPaintListener(new PaintListener() {

            /**
             * This paint control allows the virtual time graph refresh to occur on paint
             * events instead of just scrolling the time axis or zooming. To avoid
             * refreshing the model on every paint event, we use a TmfUiRefreshHandler to
             * coalesce requests and only execute the last one, we also check if the entries
             * have changed to avoid useless model refresh.
             *
             * @param e
             *            paint event on the visible area
             */
            @Override
            public void paintControl(PaintEvent e) {
                TmfUiRefreshHandler.getInstance().queueUpdate(this, () -> {
                    if (timeGraphControl.isDisposed()) {
                        return;
                    }
                    Set<@NonNull TimeGraphEntry> newSet = getVisibleItems(DEFAULT_BUFFER_SIZE);
                    if (!fVisibleEntries.equals(newSet)) {
                        /*
                         * Start a zoom thread if the set of visible entries has changed. We do not use
                         * lists as the order is not important. We cannot use the start index / size of
                         * the visible entries as we can collapse / reorder events.
                         */
                        fVisibleEntries = newSet;
                        startZoomThread(getTimeGraphViewer().getTime0(), getTimeGraphViewer().getTime1(), false);
                    }
                });
            }
        });
    }

    /**
     * Get the time graph viewer
     *
     * @return the time graph viewer
     */
    @VisibleForTesting
    public TimeGraphViewer getTimeGraphViewer() {
        return fTimeGraphViewer;
    }

    /**
     * Handler for the trace selected signal
     *
     * @param signal
     *            The incoming signal
     */
    @TmfSignalHandler
    public void traceSelected(final TmfTraceSelectedSignal signal) {
        ITmfTrace trace = signal.getTrace();

        fTrace = trace;
        if (trace == null) {
            return;
        }
        // If entries for this trace are already available, just zoom on them,
        // otherwise, rebuild
        List<@NonNull TimeGraphEntry> list = fEntryListMap.get(trace);
        if (list == null) {
            refresh();
            Display.getDefault().asyncExec(() -> buildFlameGraph(trace, null, null));
        } else {
            // Reset end time
            long endTime = Long.MIN_VALUE;
            for (TimeGraphEntry entry : list) {
                endTime = Math.max(endTime, entry.getEndTime());
            }
            setEndTime(endTime);
            refresh();
            startZoomThread(0, endTime, false);
        }

    }

    /**
     * Get the trace this view is for
     *
     * @return The trace, or <code>null</code> if no trace is set
     */
    protected @Nullable ITmfTrace getTrace() {
        return fTrace;
    }

    /**
     * Get the callgraph modules used to build the view
     *
     * @return The call graph provider modules
     */
    protected Iterable<ICallGraphProvider> getCallgraphModules() {
        ITmfTrace trace = fTrace;
        if (trace == null) {
            return null;
        }
        String analysisId = NonNullUtils.nullToEmptyString(getViewSite().getSecondaryId());
        Iterable<ICallGraphProvider> modules = TmfTraceUtils.getAnalysisModulesOfClass(trace, ICallGraphProvider.class);
        return StreamSupport.stream(modules.spliterator(), false)
                .filter(m -> {
                    if (m instanceof IAnalysisModule) {
                        return ((IAnalysisModule) m).getId().equals(analysisId);
                    }
                    return true;
                })
                .collect(Collectors.toSet());
    }

    private String getProviderId() {
        String secondaryId = this.getViewSite().getSecondaryId();
        return (secondaryId == null) ? FlameGraphDataProvider.ID : FlameGraphDataProvider.ID + ':' + secondaryId;
    }

    private class BuildRunnable {
        private final @NonNull ITmfTrace fBuildTrace;
        private final @NonNull ITmfTrace fParentTrace;
        private final @NonNull FlowScopeLog fScope;

        public BuildRunnable(final @NonNull ITmfTrace trace, final @NonNull ITmfTrace parentTrace, final @NonNull FlowScopeLog log) {
            fBuildTrace = trace;
            fParentTrace = parentTrace;
            fScope = log;
        }

        public void run(IProgressMonitor monitor) {
            try (FlowScopeLog log = new FlowScopeLogBuilder(LOGGER, Level.FINE, "FlameGraphView:BuildThread", "trace", fBuildTrace.getName()).setParentScope(fScope).build()) { //$NON-NLS-1$ //$NON-NLS-2$
                buildEntryList(fBuildTrace, fParentTrace, NonNullUtils.checkNotNull(monitor));
                synchronized (fBuildJobMap) {
                    fBuildJobMap.remove(fBuildTrace);
                }
            }
        }
    }

    /**
     * @param trace
     * @param parentTrace
     * @param monitor
     */
    protected void buildEntryList(@NonNull ITmfTrace trace, @NonNull ITmfTrace parentTrace, @NonNull IProgressMonitor monitor) {
        ITimeGraphDataProvider<@NonNull TimeGraphEntryModel> dataProvider = DataProviderManager
                .getInstance().getDataProvider(trace, getProviderId(), ITimeGraphDataProvider.class);
        if (dataProvider == null) {
            return;
        }
        boolean complete = false;
        while (!complete && !monitor.isCanceled()) {
            Map<String, Object> parameters = new HashMap<>();
            parameters.put(DataProviderParameterUtils.REQUESTED_TIME_KEY, ImmutableList.of(0, Long.MAX_VALUE));
            ICallStackGroupDescriptor groupBy = fGroupBy;
            if (groupBy != null) {
                parameters.put(FlameGraphDataProvider.GROUP_BY_KEY, groupBy.getName());
            }
            TmfModelResponse<TmfTreeModel<@NonNull TimeGraphEntryModel>> response = dataProvider.fetchTree(parameters, monitor);
            if (response.getStatus() == ITmfResponse.Status.FAILED) {
                Activator.getDefault().logError(getClass().getSimpleName() + " Data Provider failed: " + response.getStatusMessage()); //$NON-NLS-1$
                return;
            } else if (response.getStatus() == ITmfResponse.Status.CANCELLED) {
                return;
            }
            complete = response.getStatus() == ITmfResponse.Status.COMPLETED;


            TmfTreeModel<@NonNull TimeGraphEntryModel> model = response.getModel();
            long endTime = Long.MIN_VALUE;
            if (model != null) {
                Map<Long, TimeGraphEntry> entries;
                synchronized (fEntries) {
                    entries = fEntries.computeIfAbsent(dataProvider, dp -> new HashMap<>());
                    /*
                     * The provider may send entries unordered and parents may
                     * not exist when child is constructor, we'll re-unite
                     * families at the end
                     */
                    List<TimeGraphEntry> orphaned = new ArrayList<>();
                    for (TimeGraphEntryModel entry : model.getEntries()) {
                        TimeGraphEntry uiEntry = entries.get(entry.getId());
                        if (entry.getParentId() != -1) {
                            if (uiEntry == null) {
                                uiEntry = new TimeGraphEntry(entry);
                                TimeGraphEntry parent = entries.get(entry.getParentId());
                                if (parent != null) {
                                    parent.addChild(uiEntry);
                                } else {
                                    orphaned.add(uiEntry);
                                }
                                entries.put(entry.getId(), uiEntry);
                            } else {
                                uiEntry.updateModel(entry);
                            }
                        } else {
                            endTime = Long.max(endTime, entry.getEndTime() + 1);

                            if (uiEntry != null) {
                                uiEntry.updateModel(entry);
                            } else {
                                // Do not assume that parentless entries are
                                // trace entries
                                uiEntry = new ParentEntry(entry, dataProvider);
                                entries.put(entry.getId(), uiEntry);
                                addToEntryList(parentTrace, Collections.singletonList(uiEntry));
                            }
                        }
                    }
                    setEndTime(endTime);
                    // Find missing parents
                    for (TimeGraphEntry orphanedEntry : orphaned) {
                        TimeGraphEntry parent = entries.get(orphanedEntry.getEntryModel().getParentId());
                        if (parent != null) {
                            parent.addChild(orphanedEntry);
                        }
                    }
                }

                long start = 0;
                long end = getEndTime();
                final long resolution = Long.max(1, (end - start) / getDisplayWidth());
                zoomEntries(ImmutableList.copyOf(entries.values()), start, end, resolution, monitor);
            }

            if (monitor.isCanceled()) {
                return;
            }

            if (parentTrace.equals(getTrace())) {

                refresh();
            }
            monitor.worked(1);

            if (!complete && !monitor.isCanceled()) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Activator.getDefault().logError("Failed to wait for data provider", e); //$NON-NLS-1$
                }
            }
        }
    }

    /**
     * Zoom thread
     *
     * @since 1.1
     */
    protected class ZoomThread extends Thread {
        private final long fZoomStartTime;
        private final long fZoomEndTime;
        private final long fResolution;
        private int fScopeId = -1;
        private final @NonNull IProgressMonitor fMonitor;
        private @NonNull Collection<@NonNull TimeGraphEntry> fCurrentEntries;
        private boolean fForce;


        /**
         * Constructor
         *
         * @param entries
         *            The entries to zoom on
         * @param startTime
         *            the start time
         * @param endTime
         *            the end time
         * @param resolution
         *            the resolution
         * @param force
         *            Whether to force the zoom of all entries or only those
         *            that have not the same sampling
         */
        public ZoomThread(@NonNull Collection<@NonNull TimeGraphEntry> entries, long startTime, long endTime, long resolution, boolean force) {
            super(FlameGraphView.this.getName() + " zoom"); //$NON-NLS-1$
            fZoomStartTime = startTime;
            fZoomEndTime = endTime;
            fResolution = resolution;
            fCurrentEntries = entries;
            fMonitor = new NullProgressMonitor();
            fForce = force;
        }

        /**
         * @return the zoom start time
         */
        public long getZoomStartTime() {
            return fZoomStartTime;
        }

        /**
         * @return the zoom end time
         */
        public long getZoomEndTime() {
            return fZoomEndTime;
        }

        /**
         * @return the resolution
         */
        public long getResolution() {
            return fResolution;
        }

        /**
         * @return the monitor
         */
        public @NonNull IProgressMonitor getMonitor() {
            return fMonitor;
        }

        /**
         * Cancel the zoom thread
         */
        public void cancel() {
            fMonitor.setCanceled(true);
        }

        @Override
        public final void run() {
            try (FlowScopeLog log = new FlowScopeLogBuilder(LOGGER, Level.FINE, "FlameGraphView:ZoomThread", "start", fZoomStartTime, "end", fZoomEndTime).setCategoryAndId(getViewId(), fScopeId).build()) { //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                if (fCurrentEntries.isEmpty()) {
                    // No rows to zoom on
                    return;
                }
                Sampling sampling = new Sampling(getZoomStartTime(), getZoomEndTime(), getResolution());
                Iterable<@NonNull TimeGraphEntry> incorrectSample = fForce ? fCurrentEntries : Iterables.filter(fCurrentEntries, entry -> !sampling.equals(entry.getSampling()));
                zoomEntries(incorrectSample, getZoomStartTime(), getZoomEndTime(), getResolution(), getMonitor());
            } finally {
                if (fDirty.decrementAndGet() < 0) {
                    Activator.getDefault().logError("Dirty underflow", new Throwable());
                }
            }
        }

        /**
         * Set the ID of the calling flow scope. This data will allow to determine the
         * causality between the zoom thread and its caller if tracing is enabled.
         *
         * @param scopeId
         *            The ID of the calling flow scope
         * @since 3.0
         */
        public void setScopeId(int scopeId) {
            fScopeId = scopeId;
        }
    }

    /**
     * Start or restart the zoom thread.
     *
     * @param startTime
     *            the zoom start time
     * @param endTime
     *            the zoom end time
     * @param force Whether to force the fetch of all rows, or only those that don't have the same range
     */
    protected final void startZoomThread(long startTime, long endTime, boolean force) {
        ITmfTrace trace = getTrace();
        if (trace == null) {
            return;
        }
        try (FlowScopeLog log = new FlowScopeLogBuilder(LOGGER, Level.FINE, "FlameGraphView:ZoomThreadCreated").setCategory(getViewId()).build()) { //$NON-NLS-1$
            long clampedStartTime = Math.max(0, Math.min(startTime, getEndTime()));
            long clampedEndTime = Math.min(getEndTime(), Math.max(endTime, 0));
            // Ignore if end time < start time, data has not been set correctly [yet]
            if (clampedEndTime < clampedStartTime) {
                return;
            }
            fDirty.incrementAndGet();
            ZoomThread zoomThread = fZoomThread;
            if (zoomThread != null) {
                zoomThread.cancel();
            }
            int timeSpace = getTimeGraphViewer().getTimeSpace();
            if (timeSpace > 0) {
                long resolution = Long.max(1, (clampedEndTime - clampedStartTime) / timeSpace);
                zoomThread = new ZoomThread(getVisibleItems(DEFAULT_BUFFER_SIZE), clampedStartTime, clampedEndTime, resolution, force);
            } else {
                zoomThread = null;
            }
            fZoomThread = zoomThread;
            if (zoomThread != null) {
                zoomThread.setScopeId(log.getId());
                /*
                 * Don't start a new thread right away if results are being applied from an old
                 * ZoomThread. Otherwise, the old results might overwrite the new results if it
                 * finishes after.
                 */
                synchronized (fZoomThreadResultLock) {
                    zoomThread.start();
                    // zoomThread decrements, so we increment here
                    fDirty.incrementAndGet();
                }
            }
        } finally {
            if (fDirty.decrementAndGet() < 0) {
                Activator.getDefault().logError("Dirty underflow", new Throwable());
            }
        }
    }

    private @NonNull Set<@NonNull TimeGraphEntry> getVisibleItems(int buffer) {

        TimeGraphControl timeGraphControl = fTimeGraphViewer.getTimeGraphControl();
        if (timeGraphControl.isDisposed()) {
            return Collections.emptySet();
        }

        int start = Integer.max(0, fTimeGraphViewer.getTopIndex() - buffer);
        int end = Integer.min(fTimeGraphViewer.getExpandedElementCount() - 1,
                fTimeGraphViewer.getTopIndex() + timeGraphControl.countPerPage() + buffer);

        Set<@NonNull TimeGraphEntry> visible = new HashSet<>(end - start + 1);
        for (int i = start; i <= end; i++) {
            /*
             * Use the getExpandedElement by index to avoid creating a copy of
             * all the the elements.
             */
            TimeGraphEntry element = (TimeGraphEntry) timeGraphControl.getExpandedElement(i);
            if (element != null) {
                visible.add(element);
            }
        }
        return visible;

    }

    private void zoomEntries(@NonNull Iterable<@NonNull TimeGraphEntry> entries, long zoomStartTime, long zoomEndTime, long resolution, @NonNull IProgressMonitor monitor) {
        if (resolution < 0) {
            // StateSystemUtils.getTimes would throw an illegal argument exception.
            return;
        }

        long start = Long.min(zoomStartTime, zoomEndTime);
        long end = Long.max(zoomStartTime, zoomEndTime);
        List<@NonNull Long> times = StateSystemUtils.getTimes(start, end, resolution);
        Sampling sampling = new Sampling(start, end, resolution);
        Multimap<ITimeGraphDataProvider<? extends TimeGraphEntryModel>, Long> providersToModelIds = filterGroupEntries(entries, zoomStartTime, zoomEndTime);
        SubMonitor subMonitor = SubMonitor.convert(monitor, getClass().getSimpleName() + "#zoomEntries", providersToModelIds.size()); //$NON-NLS-1$

        for (Entry<ITimeGraphDataProvider<? extends TimeGraphEntryModel>, Collection<Long>> entry : providersToModelIds.asMap().entrySet()) {
            ITimeGraphDataProvider<? extends TimeGraphEntryModel> dataProvider = entry.getKey();
            SelectionTimeQueryFilter filter = new SelectionTimeQueryFilter(times, entry.getValue());
            Map<@NonNull String, @NonNull Object> parameters = FetchParametersUtils.selectionTimeQueryToMap(filter);
//            Multimap<@NonNull Integer, @NonNull String> regexesMap = getRegexes();
//            if (!regexesMap.isEmpty()) {
//                parameters.put(DataProviderParameterUtils.REGEX_MAP_FILTERS_KEY, regexesMap.asMap());
//            }
            TmfModelResponse<TimeGraphModel> response = dataProvider.fetchRowModel(parameters, monitor);

            TimeGraphModel model = response.getModel();
            if (model != null) {
                zoomEntries(fEntries.get(dataProvider), model.getRows(), response.getStatus() == ITmfResponse.Status.COMPLETED, sampling);
            }
            subMonitor.worked(1);
        }
        redraw();
    }

    private void zoomEntries(Map<Long, TimeGraphEntry> map, List<ITimeGraphRowModel> model, boolean completed, Sampling sampling) {
        boolean isZoomThread = false; // Thread.currentThread() instanceof ZoomThread;
        for (ITimeGraphRowModel rowModel : model) {
            TimeGraphEntry entry = map.get(rowModel.getEntryID());

            if (entry != null) {
                List<ITimeEvent> events = createTimeEvents(entry, rowModel.getStates());
                if (isZoomThread) {
                    synchronized (fZoomThreadResultLock) {
                        Display.getDefault().asyncExec(() -> {
                            entry.setZoomedEventList(events);
                            if (completed) {
                                entry.setSampling(sampling);
                            }
                        });
                    }
                } else {
                    entry.setEventList(events);
                }
            }
        }
    }

    /**
     * Create {@link ITimeEvent}s for an entry from the list of
     * {@link ITimeGraphState}s, filling in the gaps.
     *
     * @param entry
     *            the {@link TimeGraphEntry} on which we are working
     * @param values
     *            the list of {@link ITimeGraphState}s from the
     *            {@link ITimeGraphDataProvider}.
     * @return a contiguous List of {@link ITimeEvent}s
     */
    private List<ITimeEvent> createTimeEvents(TimeGraphEntry entry, List<ITimeGraphState> values) {
        List<ITimeEvent> events = new ArrayList<>(values.size());
        ITimeEvent prev = null;
        for (ITimeGraphState state : values) {
            ITimeEvent event = createTimeEvent(entry, state);
            if (prev != null) {
                long prevEnd = prev.getTime() + prev.getDuration();
                if (prevEnd < event.getTime()) {
                    // fill in the gap.
                    TimeEvent timeEvent = new TimeEvent(entry, prevEnd, event.getTime() - prevEnd);
                    events.add(timeEvent);
                }
            }
            prev = event;
            events.add(event);
        }
        return events;
    }

    /**
     * Create a {@link TimeEvent} for a {@link TimeGraphEntry} and a
     * {@link TimeGraphState}
     *
     * @param entry
     *            {@link TimeGraphEntry} for which we create a state
     * @param state
     *            {@link ITimeGraphState} from the data provider
     * @return a new {@link TimeEvent} for these arguments
     *
     * @since 3.3
     */
    protected TimeEvent createTimeEvent(TimeGraphEntry entry, ITimeGraphState state) {
        String label = state.getLabel();
        if (state.getValue() == Integer.MIN_VALUE && label == null) {
            return new NullTimeEvent(entry, state.getStartTime(), state.getDuration());
        }
        if (label != null) {
            return new NamedTimeEvent(entry, state.getStartTime(), state.getDuration(), state.getValue(), label, state.getActiveProperties());
        }
        return new TimeEvent(entry, state.getStartTime(), state.getDuration(), state.getValue(), state.getActiveProperties());
    }

    /**
     * Filter the entries to return only the Non Null {@link TimeGraphEntry} which
     * intersect the time range.
     *
     * @param visible
     *            the input list of visible entries
     * @param zoomStartTime
     *            the leftmost time bound of the view
     * @param zoomEndTime
     *            the rightmost time bound of the view
     * @return A Multimap of data providers to their visible entries' model IDs.
     */
    private static Multimap<ITimeGraphDataProvider<? extends TimeGraphEntryModel>, Long> filterGroupEntries(Iterable<TimeGraphEntry> visible,
            long zoomStartTime, long zoomEndTime) {
        Multimap<ITimeGraphDataProvider<? extends TimeGraphEntryModel>, Long> providersToModelIds = HashMultimap.create();
        for (TimeGraphEntry entry : visible) {
            if (zoomStartTime <= entry.getEndTime() && zoomEndTime >= entry.getStartTime() && entry.hasTimeEvents()) {
                ITimeGraphDataProvider<? extends TimeGraphEntryModel> provider = getProvider(entry);
                if (provider != null) {
                    providersToModelIds.put(provider, entry.getEntryModel().getId());
                }
            }
        }
        return providersToModelIds;
    }

    /**
     * Get the {@link ITimeGraphDataProvider} from a {@link TimeGraphEntry}'s
     * parent.
     *
     * @param entry
     *            queried {@link TimeGraphEntry}.
     * @return the {@link ITimeGraphDataProvider}
     * @since 3.3
     */
    public static ITimeGraphDataProvider<? extends TimeGraphEntryModel> getProvider(ITimeGraphEntry entry) {
        ITimeGraphEntry parent = entry;
        while (parent != null) {
            if (parent instanceof ParentEntry) {
                return ((ParentEntry) parent).getProvider();
            }
            parent = parent.getParent();
        }
        throw new IllegalStateException(entry + " should have a TraceEntry parent"); //$NON-NLS-1$
    }

    private void refresh() {
        try (FlowScopeLog parentLogger = new FlowScopeLogBuilder(LOGGER, Level.FINE, "FlameGraphView:RefreshRequested").setCategory(getViewId()).build()) { //$NON-NLS-1$
            final boolean isZoomThread = Thread.currentThread() instanceof ZoomThread;
            TmfUiRefreshHandler.getInstance().queueUpdate(this, () -> {
                try (FlowScopeLog log = new FlowScopeLogBuilder(LOGGER, Level.FINE, "FlameGraphView:Refresh").setParentScope(parentLogger).build()) { //$NON-NLS-1$
                    if (fTimeGraphViewer.getControl().isDisposed()) {
                        return;
                    }
                    fDirty.incrementAndGet();
                    try {
                        List<TimeGraphEntry> entries;
                        synchronized (fEntryListMap) {
                            entries = fEntryListMap.get(getTrace());
                            if (entries == null) {
                                entries = new CopyOnWriteArrayList<>();
                            } else if (fEntryComparator != null) {
                                List<TimeGraphEntry> list = new ArrayList<>(entries);
                                Collections.sort(list, fEntryComparator);
                                for (ITimeGraphEntry entry : list) {
                                    sortChildren(entry, fEntryComparator);
                                }
                                entries.clear();
                                entries.addAll(list);
                            }
                        }

                        boolean inputChanged = entries != fTimeGraphViewer.getInput();
                        if (inputChanged) {
                            fTimeGraphViewer.setInput(entries);
                        } else {
                            fTimeGraphViewer.refresh();
                        }

                        long startBound = 0;
                        long endBound = getEndTime();
                        endBound = (endBound == Long.MIN_VALUE ? SWT.DEFAULT : endBound);
                        fTimeGraphViewer.setTimeBounds(startBound, endBound);

                        if (inputChanged && !isZoomThread) {
                            fTimeGraphViewer.resetStartFinishTime();
                        }

                    } finally {
                        if (fDirty.decrementAndGet() < 0) {
                            Activator.getDefault().logError("Dirty underflow", new Throwable());
                        }
                    }
                }
            });
        }
    }

    private void redraw() {

        try (FlowScopeLog flowParent = new FlowScopeLogBuilder(LOGGER, Level.FINE, "FlameGraphView:RedrawRequested").setCategory(getViewId()).build()) { //$NON-NLS-1$
            Display.getDefault().asyncExec(() -> {
                try (FlowScopeLog log = new FlowScopeLogBuilder(LOGGER, Level.FINE, "FlameGraphView:Redraw").setParentScope(flowParent).build()) { //$NON-NLS-1$
                    if (fTimeGraphViewer.getControl().isDisposed()) {
                        return;
                    }
                    fTimeGraphViewer.getControl().redraw();
                    fTimeGraphViewer.getControl().update();
                }
            });
        }
    }

    private void sortChildren(ITimeGraphEntry entry, Comparator<ITimeGraphEntry> comparator) {
        if (entry instanceof TimeGraphEntry) {
            ((TimeGraphEntry) entry).sortChildren(comparator);
        }
        for (ITimeGraphEntry child : entry.getChildren()) {
            sortChildren(child, comparator);
        }
    }

    private int getDisplayWidth() {
        int displayWidth = fDisplayWidth;
        return displayWidth <= 0 ? 1 : displayWidth;
    }

    /**
     * A class for parent entries that contain a link to the data provider
     *
     * @author Geneviève Bastien
     */
    private static class ParentEntry extends TimeGraphEntry {
        private final @NonNull ITimeGraphDataProvider<? extends TimeGraphEntryModel> fProvider;

        /**
         * Constructor
         *
         * @param model
         *            trace level model
         * @param provider
         *            reference to the provider for this trace and view
         */
        public ParentEntry(@NonNull TimeGraphEntryModel model,
                @NonNull ITimeGraphDataProvider<? extends TimeGraphEntryModel> provider) {
            super(model);
            fProvider = provider;
        }

        /**
         * Getter for the data provider for this {@link ParentEntry}
         *
         * @return this entry's {@link ITimeGraphDataProvider}
         */
        public @NonNull ITimeGraphDataProvider<? extends TimeGraphEntryModel> getProvider() {
            return fProvider;
        }
    }

    private synchronized void setEndTime(long endTime) {
        fEndTime = endTime;
    }

    private long getEndTime() {
        return fEndTime;
    }

    /**
     * Adds a list of entries to a trace's entry list
     *
     * @param trace
     *            the trace
     * @param list
     *            the list of time graph entries to add
     */
    private void addToEntryList(ITmfTrace trace, List<@NonNull TimeGraphEntry> list) {
        synchronized (fEntryListMap) {
            List<TimeGraphEntry> entryList = fEntryListMap.get(trace);
            if (entryList == null) {
                fEntryListMap.put(trace, new CopyOnWriteArrayList<>(list));
            } else {
                for (TimeGraphEntry entry : list) {
                    if (!entryList.contains(entry)) {
                        entryList.add(entry);
                    }
                }
            }
        }
    }

    private void resetEntries(ITmfTrace trace) {
        synchronized (fEntryListMap) {
            synchronized (fEntries) {
                // Remove the entries from the entry list map and from the
                // fEntries cache
                List<@NonNull TimeGraphEntry> entries = fEntryListMap.remove(trace);
                if (entries == null) {
                    return;
                }
                for (TimeGraphEntry entry : entries) {
                    if (entry instanceof ParentEntry) {
                        fEntries.remove(((ParentEntry) entry).getProvider());
                    }
                }
                refresh();
            }
        }
    }

    /**
     * Get the necessary data for the flame graph and display it
     *
     * @param viewTrace
     *            the trace
     * @param selStart
     *            The selection start timestamp or <code>null</code> to show all
     *            data
     * @param selEnd
     *            The selection end timestamp or <code>null</code> to show all
     *            data
     */
    @VisibleForTesting
    public void buildFlameGraph(@NonNull ITmfTrace viewTrace, @Nullable ITmfTimestamp selStart, @Nullable ITmfTimestamp selEnd) {
        /*
         * Note for synchronization:
         *
         * Acquire the lock at entry. then we have 4 places to release it
         *
         * 1- if the lock failed
         *
         * 2- if the data is null and we have no UI to update
         *
         * 3- when the job starts running and can thus be canceled
         */
        try (FlowScopeLog log = new FlowScopeLogBuilder(LOGGER, Level.FINE, "FlameGraphView:Building").setCategory(getViewId()).build()) { //$NON-NLS-1$
            try {
                fLock.acquire();
            } catch (InterruptedException e) {
                Activator.getDefault().logError(e.getMessage(), e);
                fLock.release();
            }

            // Run the build jobs through the site progress service if available
            IWorkbenchSiteProgressService service = null;
            IWorkbenchPartSite site = getSite();
            if (site != null) {
                service = site.getService(IWorkbenchSiteProgressService.class);
            }

            for (ITmfTrace trace : TmfTraceManager.getTraceSetWithExperiment(viewTrace)) {

                // Cancel previous build job for this trace
                Job buildJob = fBuildJobMap.remove(trace);
                if (buildJob != null) {
                    buildJob.cancel();
                }
                resetEntries(viewTrace);
                buildJob = new Job(getTitle() + Messages.FlameGraphView_RetrievingData) {
                    @Override
                    protected IStatus run(IProgressMonitor monitor) {
                        new BuildRunnable(trace, viewTrace, log).run(monitor);
                        monitor.done();
                        return Status.OK_STATUS;
                    }
                };
                fBuildJobMap.put(trace, buildJob);
                if (service != null) {
                    service.schedule(buildJob);
                } else {
                    buildJob.schedule();
                }
            }
            fLock.release();
        }
    }

    /**
     * Await the next refresh
     *
     * @return Whether the view is ready with new data
     *
     * @throws InterruptedException
     *             something took too long
     */
    @VisibleForTesting
    public boolean isDirty() throws InterruptedException {
        /*
         * wait for the semaphore to be available, then release it immediately
         * and verify dirtiness
         */
        fLock.acquire();
        fLock.release();
        return (fDirty.get() != 0);
    }

    /**
     * Trace is closed: clear the data structures and the view
     *
     * @param signal
     *            the signal received
     */
    @TmfSignalHandler
    public void traceClosed(final TmfTraceClosedSignal signal) {
        if (signal.getTrace() == fTrace) {
            fTimeGraphViewer.setInput(null);
        }
    }

    @Override
    public void setFocus() {
        fTimeGraphViewer.setFocus();
    }

    // ------------------------------------------------------------------------
    // Helper methods
    // ------------------------------------------------------------------------

    private void createTimeEventContextMenu() {
        fEventMenuManager.setRemoveAllWhenShown(true);
        TimeGraphControl timeGraphControl = fTimeGraphViewer.getTimeGraphControl();
        final Menu timeEventMenu = fEventMenuManager.createContextMenu(timeGraphControl);

        timeGraphControl.addTimeGraphEntryMenuListener(new MenuDetectListener() {
            @Override
            public void menuDetected(MenuDetectEvent event) {
                /*
                 * The TimeGraphControl will call the TimeGraphEntryMenuListener
                 * before the TimeEventMenuListener. We need to clear the menu
                 * for the case the selection was done on the namespace where
                 * the time event listener below won't be called afterwards.
                 */
                timeGraphControl.setMenu(null);
                event.doit = false;
            }
        });
        timeGraphControl.addTimeEventMenuListener(new MenuDetectListener() {
            @Override
            public void menuDetected(MenuDetectEvent event) {
                Menu menu = timeEventMenu;
                if (event.data instanceof TimeEvent) {
                    if (((TimeEvent) event.data).hasValue()) {
                        timeGraphControl.setMenu(menu);
                    }
                    return;
                }
                timeGraphControl.setMenu(null);
                event.doit = false;
            }
        });

        fEventMenuManager.addMenuListener(new IMenuListener() {
            @Override
            public void menuAboutToShow(IMenuManager manager) {
                fillTimeEventContextMenu(fEventMenuManager);
                fEventMenuManager.add(new GroupMarker(IWorkbenchActionConstants.MB_ADDITIONS));
            }
        });
        getSite().registerContextMenu(fEventMenuManager, fTimeGraphViewer.getSelectionProvider());
    }

    /**
     * Fill context menu
     *
     * @param menuManager
     *            a menuManager to fill
     */
    protected void fillTimeEventContextMenu(@NonNull IMenuManager menuManager) {
        // TODO Bring this functionality back
//        ISelection selection = getSite().getSelectionProvider().getSelection();
//        if (selection instanceof IStructuredSelection) {
//            for (Object object : ((IStructuredSelection) selection).toList()) {
//                if (object instanceof FlamegraphEvent) {
//                    final FlamegraphEvent flamegraphEvent = (FlamegraphEvent) object;
//                    final ICalledFunction maxSeg = flamegraphEvent.getMaxObject();
//                    if (maxSeg != null) {
//                        menuManager.add(new Action(Messages.FlameGraphView_GotoMaxDuration) {
//                            @Override
//                            public void run() {
//                                TmfSelectionRangeUpdatedSignal sig = new TmfSelectionRangeUpdatedSignal(this, TmfTimestamp.fromNanos(maxSeg.getStart()), TmfTimestamp.fromNanos(maxSeg.getEnd()), fTrace);
//                                broadcast(sig);
//                            }
//                        });
//                    }
//
//                    final ICalledFunction minSeg = flamegraphEvent.getMinObject();
//                    if (minSeg != null) {
//                        menuManager.add(new Action(Messages.FlameGraphView_GotoMinDuration) {
//                            @Override
//                            public void run() {
//                                TmfSelectionRangeUpdatedSignal sig = new TmfSelectionRangeUpdatedSignal(this, TmfTimestamp.fromNanos(minSeg.getStart()), TmfTimestamp.fromNanos(minSeg.getEnd()), fTrace);
//                                broadcast(sig);
//                            }
//                        });
//                    }
//                }
//            }
//        }
    }

    private void contributeToActionBars() {
        IActionBars bars = getViewSite().getActionBars();
        fillLocalToolBar(bars.getToolBarManager());
    }

    private void fillLocalToolBar(IToolBarManager manager) {
        manager.add(getConfigureSymbolsAction());
        manager.add(getAggregateByAction());
        manager.add(getSortByNameAction());
        manager.add(getSortByIdAction());
        manager.add(new Separator());
    }

    private Action getAggregateByAction() {
        if (fAggregateByAction == null) {
            fAggregateByAction = new Action(Messages.FlameGraphView_GroupByName, IAction.AS_DROP_DOWN_MENU) {
                @Override
                public void run() {
                    SortOption sortOption = fSortOption;
                    if (sortOption == SortOption.BY_NAME) {
                        setSortOption(SortOption.BY_NAME_REV);
                    } else {
                        setSortOption(SortOption.BY_NAME);
                    }
                }
            };
            fAggregateByAction.setToolTipText(Messages.FlameGraphView_GroupByTooltip);
            fAggregateByAction.setImageDescriptor(AGGREGATE_BY_ICON);
            fAggregateByAction.setMenuCreator(new IMenuCreator() {
                Menu menu = null;

                @Override
                public void dispose() {
                    if (menu != null) {
                        menu.dispose();
                        menu = null;
                    }
                }

                @Override
                public Menu getMenu(Control parent) {
                    if (menu != null) {
                        menu.dispose();
                    }
                    menu = new Menu(parent);
                    Iterable<ICallGraphProvider> callgraphModules = getCallgraphModules();
                    Iterator<ICallGraphProvider> iterator = callgraphModules.iterator();
                    if (!iterator.hasNext()) {
                        return menu;
                    }
                    ICallGraphProvider provider = iterator.next();
                    // Add the all group element
                    Action allGroupAction = createActionForGroup(AllGroupDescriptor.getInstance());
                    new ActionContributionItem(allGroupAction).fill(menu, -1);
                    Collection<ICallStackGroupDescriptor> series = provider.getGroupDescriptors();
                    series.forEach(group -> {
                        ICallStackGroupDescriptor subGroup = group;
                        do {
                            Action groupAction = createActionForGroup(subGroup);
                            new ActionContributionItem(groupAction).fill(menu, -1);
                            subGroup = subGroup.getNextGroup();
                        } while (subGroup != null);
                    });
                    return menu;
                }

                @Override
                public Menu getMenu(Menu parent) {
                    return null;
                }
            });
        }
        return fAggregateByAction;
    }

    private Action createActionForGroup(ICallStackGroupDescriptor descriptor) {
        return new Action(descriptor.getName(), IAction.AS_RADIO_BUTTON) {
            @Override
            public void run() {
                ITmfTrace trace = getTrace();
                if (trace == null) {
                    return;
                }
                fGroupBy = descriptor;
                buildFlameGraph(trace, null, null);
            }
        };
    }

    // --------------------------------
    // Sorting related methods
    // --------------------------------

    private Action getSortByNameAction() {
        if (fSortByNameAction == null) {
            fSortByNameAction = new Action(Messages.FlameGraph_SortByThreadName, IAction.AS_CHECK_BOX) {
                @Override
                public void run() {
                    SortOption sortOption = fSortOption;
                    if (sortOption == SortOption.BY_NAME) {
                        setSortOption(SortOption.BY_NAME_REV);
                    } else {
                        setSortOption(SortOption.BY_NAME);
                    }
                }
            };
            fSortByNameAction.setToolTipText(Messages.FlameGraph_SortByThreadName);
            fSortByNameAction.setImageDescriptor(SORT_BY_NAME_ICON);
        }
        return fSortByNameAction;
    }

    private Action getSortByIdAction() {
        if (fSortByIdAction == null) {
            fSortByIdAction = new Action(Messages.FlameGraph_SortByThreadId, IAction.AS_CHECK_BOX) {
                @Override
                public void run() {
                    SortOption sortOption = fSortOption;
                    if (sortOption == SortOption.BY_ID) {
                        setSortOption(SortOption.BY_ID_REV);
                    } else {
                        setSortOption(SortOption.BY_ID);
                    }
                }
            };
            fSortByIdAction.setToolTipText(Messages.FlameGraph_SortByThreadId);
            fSortByIdAction.setImageDescriptor(SORT_BY_ID_ICON);
        }
        return fSortByIdAction;
    }

    private void setSortOption(SortOption sortOption) {
        // reset defaults
        getSortByNameAction().setChecked(false);
        getSortByNameAction().setImageDescriptor(SORT_BY_NAME_ICON);
        getSortByIdAction().setChecked(false);
        getSortByIdAction().setImageDescriptor(SORT_BY_ID_ICON);

        if (sortOption.equals(SortOption.BY_NAME)) {
            fSortOption = SortOption.BY_NAME;
            getSortByNameAction().setChecked(true);
        } else if (sortOption.equals(SortOption.BY_NAME_REV)) {
            fSortOption = SortOption.BY_NAME_REV;
            getSortByNameAction().setChecked(true);
            getSortByNameAction().setImageDescriptor(SORT_BY_NAME_REV_ICON);
        } else if (sortOption.equals(SortOption.BY_ID)) {
            fSortOption = SortOption.BY_ID;
            getSortByIdAction().setChecked(true);
        } else if (sortOption.equals(SortOption.BY_ID_REV)) {
            fSortOption = SortOption.BY_ID_REV;
            getSortByIdAction().setChecked(true);
            getSortByIdAction().setImageDescriptor(SORT_BY_ID_REV_ICON);
        }
        saveSortOption();
        fTimeGraphViewer.refresh();
    }

    private void saveSortOption() {
        SortOption sortOption = fSortOption;
        IDialogSettings settings = Activator.getDefault().getDialogSettings();
        IDialogSettings section = settings.getSection(getClass().getName());
        if (section == null) {
            section = settings.addNewSection(getClass().getName());
        }
        section.put(SORT_OPTION_KEY, sortOption.name());
    }

    private void loadSortOption() {
        IDialogSettings settings = Activator.getDefault().getDialogSettings();
        IDialogSettings section = settings.getSection(getClass().getName());
        if (section == null) {
            return;
        }
        String sortOption = section.get(SORT_OPTION_KEY);
        if (sortOption == null) {
            return;
        }
        setSortOption(SortOption.fromName(sortOption));
    }

    //--------------------------------
    // Symbol related methods
    //--------------------------------

    private Action getConfigureSymbolsAction() {
        if (fConfigureSymbolsAction != null) {
            return fConfigureSymbolsAction;
        }

        fConfigureSymbolsAction = new Action(Messages.FlameGraphView_ConfigureSymbolProvidersText) {
            @Override
            public void run() {
                SymbolProviderConfigDialog dialog = new SymbolProviderConfigDialog(getSite().getShell(), getProviderPages());
                if (dialog.open() == IDialogConstants.OK_ID) {
                    startZoomThread(getTimeGraphViewer().getTime0(), getTimeGraphViewer().getTime1(), true);
                }
            }
        };

        fConfigureSymbolsAction.setToolTipText(Messages.FlameGraphView_ConfigureSymbolProvidersTooltip);
        fConfigureSymbolsAction.setImageDescriptor(Activator.getDefault().getImageDescripterFromPath(SYMBOL_MAPPING_ICON_PATH));

        /*
         * The updateConfigureSymbolsAction() method (called by refresh()) will
         * set the action to true if applicable after the symbol provider has
         * been properly loaded.
         */
        fConfigureSymbolsAction.setEnabled(true);

        return fConfigureSymbolsAction;
    }

    /**
     * @return an array of {@link ISymbolProviderPreferencePage} that will
     *         configure the current traces
     */
    private ISymbolProviderPreferencePage[] getProviderPages() {
        List<ISymbolProviderPreferencePage> pages = new ArrayList<>();
        ITmfTrace trace = fTrace;
        if (trace != null) {
            for (ITmfTrace subTrace : TmfTraceManager.getTraceSet(trace)) {
                for (ISymbolProvider provider : SymbolProviderManager.getInstance().getSymbolProviders(subTrace)) {
                    if (provider instanceof org.eclipse.tracecompass.tmf.ui.symbols.ISymbolProvider) {
                        org.eclipse.tracecompass.tmf.ui.symbols.ISymbolProvider provider2 = (org.eclipse.tracecompass.tmf.ui.symbols.ISymbolProvider) provider;
                        ISymbolProviderPreferencePage page = provider2.createPreferencePage();
                        if (page != null) {
                            pages.add(page);
                        }
                    }
                }
            }
        }
        return pages.toArray(new ISymbolProviderPreferencePage[pages.size()]);
    }

    /**
     * Symbol map provider updated
     *
     * @param signal
     *            the signal
     */
    @TmfSignalHandler
    public void symbolMapUpdated(TmfSymbolProviderUpdatedSignal signal) {
        if (signal.getSource() != this) {
            startZoomThread(getTimeGraphViewer().getTime0(), getTimeGraphViewer().getTime1(), true);
        }
    }

    @Override
    protected @Nullable IAction createSaveAction() {
        return SaveImageUtil.createSaveAction(getName(), this::getTimeGraphViewer);
    }

}
