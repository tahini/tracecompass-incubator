/*******************************************************************************
 * Copyright (c) 2018 Ericsson
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.eclipse.tracecompass.incubator.internal.opentracing.ui.view.spanlife;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.graphics.RGBA;
import org.eclipse.tracecompass.incubator.internal.opentracing.core.analysis.spanlife.SpanLifeEntryModel;
import org.eclipse.tracecompass.internal.tmf.core.model.filters.FetchParametersUtils;
import org.eclipse.tracecompass.tmf.core.model.filters.SelectionTimeQueryFilter;
import org.eclipse.tracecompass.tmf.core.model.timegraph.ITimeGraphDataProvider;
import org.eclipse.tracecompass.tmf.core.model.timegraph.TimeGraphEntryModel;
import org.eclipse.tracecompass.tmf.core.presentation.IYAppearance;
import org.eclipse.tracecompass.tmf.core.response.TmfModelResponse;
import org.eclipse.tracecompass.tmf.ui.views.timegraph.BaseDataProviderTimeGraphView;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.StateItem;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.TimeGraphPresentationProvider;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.model.ITimeEvent;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.model.ITimeEventStyleStrings;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.model.ITimeGraphEntry;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.model.TimeEvent;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.model.TimeGraphEntry;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.widgets.ITmfTimeGraphDrawingHelper;
import org.eclipse.tracecompass.tmf.ui.widgets.timegraph.widgets.TimeGraphControl;

import com.google.common.collect.ImmutableMap;

/**
 * Span life presentation provider
 *
 * @author Katherine Nadeau
 */
public class SpanLifePresentationProvider extends TimeGraphPresentationProvider {

    private static final @NonNull String ERROR = "error"; //$NON-NLS-1$
    private static final @NonNull String EVENT = "event"; //$NON-NLS-1$
    private static final @NonNull String MESSAGE = "message"; //$NON-NLS-1$
    private static final @NonNull String STACK = "stack"; //$NON-NLS-1$
    private static final @NonNull String OTHER = "other"; //$NON-NLS-1$
    private static final @NonNull String FLAG_EMOJI = "🏳️"; //$NON-NLS-1$

    private static final @NonNull RGBA MARKER_COLOR = new RGBA(200, 0, 0, 150);
    private static final int MARKER_COLOR_INT = MARKER_COLOR.hashCode();
    /**
     * Only states available
     */
    private static final StateItem[] STATE_TABLE = { new StateItem(new RGB(179,205,224), "Fist Service Class"), //$NON-NLS-1$
            new StateItem(new RGB(100, 151, 177), "Second Service Class"), //$NON-NLS-1$
            new StateItem(new RGB(0,91,150), "Third Service Class"), //$NON-NLS-1$
            new StateItem(new RGB(3, 57, 108), "Forth Service Class"), //$NON-NLS-1$
            new StateItem(new RGB(1, 31, 75), "Fifth Service Class"), //$NON-NLS-1$
            new StateItem(ImmutableMap.of(ITimeEventStyleStrings.label(), ERROR, ITimeEventStyleStrings.fillColor(), MARKER_COLOR_INT, ITimeEventStyleStrings.symbolStyle(), IYAppearance.SymbolStyle.CROSS, ITimeEventStyleStrings.heightFactor(),
                    0.4f)),
            new StateItem(
                    ImmutableMap.of(ITimeEventStyleStrings.label(), EVENT, ITimeEventStyleStrings.fillColor(), MARKER_COLOR_INT, ITimeEventStyleStrings.symbolStyle(), IYAppearance.SymbolStyle.DIAMOND, ITimeEventStyleStrings.heightFactor(), 0.3f)),
            new StateItem(
                    ImmutableMap.of(ITimeEventStyleStrings.label(), MESSAGE, ITimeEventStyleStrings.fillColor(), MARKER_COLOR_INT, ITimeEventStyleStrings.symbolStyle(), IYAppearance.SymbolStyle.CIRCLE, ITimeEventStyleStrings.heightFactor(), 0.3f)),
            new StateItem(ImmutableMap.of(ITimeEventStyleStrings.label(), STACK, ITimeEventStyleStrings.fillColor(), MARKER_COLOR_INT, ITimeEventStyleStrings.symbolStyle(), IYAppearance.SymbolStyle.SQUARE,
                    ITimeEventStyleStrings.heightFactor(), 0.3f)),
            new StateItem(ImmutableMap.of(ITimeEventStyleStrings.label(), OTHER, ITimeEventStyleStrings.fillColor(), MARKER_COLOR_INT, ITimeEventStyleStrings.symbolStyle(), FLAG_EMOJI, ITimeEventStyleStrings.heightFactor(), 0.3f))
    };

    /**
     * Constructor
     */
    public SpanLifePresentationProvider() {
        super("Span"); //$NON-NLS-1$
    }

    @Override
    public StateItem[] getStateTable() {
        return STATE_TABLE;
    }

    @Override
    public Map<String, String> getEventHoverToolTipInfo(ITimeEvent event, long hoverTime) {
        Map<String, String> eventHoverToolTipInfo = super.getEventHoverToolTipInfo(event, hoverTime);
        if (eventHoverToolTipInfo == null) {
            eventHoverToolTipInfo = new LinkedHashMap<>();
        }
        ITimeGraphEntry entry = event.getEntry();
        if (entry instanceof TimeGraphEntry) {
            long id = ((TimeGraphEntry) entry).getModel().getId();
            ITimeGraphDataProvider<? extends TimeGraphEntryModel> provider = BaseDataProviderTimeGraphView.getProvider((TimeGraphEntry) entry);

            long windowStartTime = Long.MIN_VALUE;
            long windowEndTime = Long.MIN_VALUE;
            ITmfTimeGraphDrawingHelper drawingHelper = getDrawingHelper();
            if (drawingHelper instanceof TimeGraphControl) {
                TimeGraphControl timeGraphControl = (TimeGraphControl) drawingHelper;
                windowStartTime = timeGraphControl.getTimeDataProvider().getTime0();
                windowEndTime = timeGraphControl.getTimeDataProvider().getTime1();
            }

            List<@NonNull Long> times = new ArrayList<>();
            times.add(windowStartTime);
            times.add(hoverTime);
            times.add(windowEndTime);

            SelectionTimeQueryFilter filter = new SelectionTimeQueryFilter(times, Collections.singleton(id));
            TmfModelResponse<@NonNull Map<@NonNull String, @NonNull String>> tooltipResponse = provider.fetchTooltip(FetchParametersUtils.selectionTimeQueryToMap(filter), new NullProgressMonitor());
            Map<@NonNull String, @NonNull String> tooltipModel = tooltipResponse.getModel();
            if (tooltipModel != null) {
                eventHoverToolTipInfo.putAll(tooltipModel);
            }
        }
        return eventHoverToolTipInfo;
    }

    @Override
    public int getStateTableIndex(@Nullable ITimeEvent event) {
        if (event instanceof SpanMarkerEvent) {
            SpanMarkerEvent markerEvent = (SpanMarkerEvent) event;
            String type = markerEvent.getType();
            switch (type) {
            case ERROR:
                return 5;
            case EVENT:
                return 6;
            case MESSAGE:
                return 7;
            case STACK:
                return 8;
            default:
                return 9;
            }
        }
        if ((event instanceof TimeEvent) && ((TimeEvent) event).getValue() != Integer.MIN_VALUE) {
            if ((event.getEntry() instanceof TimeGraphEntry) && (((TimeGraphEntry) event.getEntry()).getModel() instanceof SpanLifeEntryModel)) {
                String processName = ((SpanLifeEntryModel) ((TimeGraphEntry) event.getEntry()).getModel()).getProcessName();
                // We want a random color but that is the same for 2 spans of the same service
                return Math.abs(Objects.hash(processName)) % 5;
            }
            return 0;
        }
        return -1;
    }
}
