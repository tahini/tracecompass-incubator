/*******************************************************************************
 * Copyright (c) 2019 École Polytechnique de Montréal
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.eclipse.tracecompass.incubator.internal.scripting.core.analysis;

import java.util.Collections;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Function;

import org.eclipse.ease.modules.ScriptParameter;
import org.eclipse.ease.modules.WrapToScript;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.swt.widgets.Display;
import org.eclipse.tracecompass.internal.tmf.analysis.xml.core.output.DataDrivenTimeGraphEntry;
import org.eclipse.tracecompass.internal.tmf.analysis.xml.core.output.DataDrivenTimeGraphProviderFactory;
import org.eclipse.tracecompass.internal.tmf.analysis.xml.ui.views.timegraph.XmlTimeGraphView;
import org.eclipse.tracecompass.internal.tmf.ui.Activator;
import org.eclipse.tracecompass.statesystem.core.ITmfStateSystemBuilder;
import org.eclipse.tracecompass.tmf.core.event.ITmfEvent;
import org.eclipse.tracecompass.tmf.core.event.ITmfEventField;
import org.eclipse.tracecompass.tmf.core.trace.ITmfTrace;
import org.eclipse.tracecompass.tmf.core.trace.TmfTraceManager;
import org.eclipse.tracecompass.tmf.core.trace.TmfTraceUtils;
import org.eclipse.tracecompass.tmf.ui.project.model.Messages;
import org.eclipse.tracecompass.tmf.ui.project.model.TraceUtils;
import org.eclipse.ui.IViewPart;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.part.WorkbenchPart;

/**
 * Provide an API to create an analysis
 *
 * @author Geneviève Bastien
 */
public class AnalysisScriptingModule {

    private static final String ENTRY_PATH = "path";
    private static final String ENTRY_DISPLAY = "display";
    private static final String ENTRY_NAME = "name";
    private static final String ENTRY_PARENT = "parent";
    private static final String ENTRY_ID = "id";

    /** Module identifier. */
    public static final String MODULE_ID = "/TraceCompass/Analysis"; //$NON-NLS-1$

    @WrapToScript
    public @Nullable ScriptedAnalysis getAnalysis(String name) {
        ITmfTrace activeTrace = TmfTraceManager.getInstance().getActiveTrace();
        if (activeTrace == null) {
            return null;
        }
        return new ScriptedAnalysis(activeTrace, name);
    }

    @WrapToScript
    public @Nullable Object getFieldValue(ITmfEvent event, String fieldName) {

        final ITmfEventField field = event.getContent().getField(fieldName);

        /* If the field does not exist, see if it's a special case */
        if (field == null) {
            // This will allow to use any column as input
            return TmfTraceUtils.resolveAspectOfNameForEvent(event.getTrace(), fieldName, event);
        }
        return field.getValue();

    }

    /**
     * @param analysis
     * @param path
     * @param name
     * @param display
     */
    @WrapToScript
    public void createTimeGraph(ScriptedAnalysis analysis, String path, @ScriptParameter(defaultValue = "") Map<String, Object> name, @ScriptParameter(defaultValue = "") String display) {
        if (name.isEmpty()) {
            System.out.println("no name");
        }
        String string = name.get("name");
        String string2 = name.get("abc");
        if (display.isEmpty()) {
            System.out.println("no display");
        }
    }

    @SuppressWarnings("restriction")
    @WrapToScript
    public @Nullable Object createTimeGraphProvider(ScriptedAnalysis analysis, Map<String, Object> data) {
        for (Entry<String, Object> entry : data.entrySet()) {
            System.out.println("entry: " + entry);
        }
        String path = String.valueOf(data.get(ENTRY_PATH));

        DataDrivenTimeGraphEntry entry = new DataDrivenTimeGraphEntry(Collections.emptyList(), path, analysis.getName(), true,
                null, null, null, null);
        DataDrivenTimeGraphProviderFactory factory = new DataDrivenTimeGraphProviderFactory(Collections.singletonList(entry), Collections.singleton(analysis.getName()), Collections.emptyList());
        ITmfStateSystemBuilder stateSystem = analysis.getStateSystem(true);
        if (stateSystem == null) {
            return null;
        }
        return factory.create(analysis.getTrace(), Collections.singletonList(stateSystem));

    }

    @WrapToScript
    public Object test(Function<String, String> bla) {
        return bla.apply("a");
    }

}
