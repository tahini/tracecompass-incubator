/*******************************************************************************
 * Copyright (c) 2017 École Polytechnique de Montréal
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.eclipse.tracecompass.incubator.callstack.core.callgraph;

import java.util.Collection;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.tracecompass.incubator.analysis.core.concepts.AggregatedCallSite;
import org.eclipse.tracecompass.incubator.analysis.core.concepts.ICallStackSymbol;
import org.eclipse.tracecompass.incubator.analysis.core.weighted.tree.IWeightedTreeGroupDescriptor;
import org.eclipse.tracecompass.incubator.analysis.core.weighted.tree.IWeightedTreeProvider;
import org.eclipse.tracecompass.incubator.analysis.core.weighted.tree.IWeightedTreeSet;
import org.eclipse.tracecompass.incubator.callstack.core.base.ICallStackElement;
import org.eclipse.tracecompass.incubator.callstack.core.base.ICallStackGroupDescriptor;
import org.eclipse.tracecompass.tmf.core.timestamp.ITmfTimestamp;


/**
 * Interface that analyses who provide callgraph
 *
 * @author Geneviève Bastien
 */
public interface ICallGraphProvider extends IWeightedTreeProvider<@NonNull ICallStackSymbol, ICallStackElement, AggregatedCallSite> {

    /**
     * Get the group descriptors that describe how the elements are grouped in this
     * call graph hierarchy. This method will return the root group descriptor.
     * Children groups can be retrieved by the parent group. For call graph
     * providers who have only one series, this will be a singleton.
     *
     * @return The collection of group descriptors for this call graph
     */
    Collection<ICallStackGroupDescriptor> getGroupDescriptors();

    @Override
    default @Nullable IWeightedTreeGroupDescriptor getGroupDescriptor() {
        // Return the first group descriptor
        Collection<ICallStackGroupDescriptor> groupDescriptors = getGroupDescriptors();
        if (groupDescriptors.isEmpty()) {
            return null;
        }
        return groupDescriptors.iterator().next();
    }

    /**
     * Get the call graph for a given time range. This callgraph is for all the
     * elements. The caller can then group the result by calling
     * {@link CallGraphGroupBy#groupCallGraphBy(ICallStackGroupDescriptor, CallGraph)}
     * method
     *
     * @param start
     *            The start of the range
     * @param end
     *            The end of the range
     * @return The call graph object containing the CCTs for each element in the
     *         range.
     */
    CallGraph getCallGraph(ITmfTimestamp start, ITmfTimestamp end);

    /**
     * Get the call graph for the full range of the trace. This callgraph is for all
     * the elements. The caller can then group the result by calling
     * {@link CallGraphGroupBy#groupCallGraphBy(ICallStackGroupDescriptor, CallGraph)}
     *
     * @return The call graph object containing the CCTs for each element in the
     *         range.
     */
    CallGraph getCallGraph();

    @Override
    default @Nullable IWeightedTreeSet<@NonNull ICallStackSymbol, ICallStackElement, AggregatedCallSite> getSelection(ITmfTimestamp start, ITmfTimestamp end) {
        CallGraph callGraph = getCallGraph(start, end);
        return callGraph;
    }

    @Override
    default IWeightedTreeSet<@NonNull ICallStackSymbol, ICallStackElement, AggregatedCallSite> getTreeSet() {
        return getCallGraph();
    }

    /**
     * Factory method to create an aggregated callsite for a symbol
     *
     * @param object
     *            The symbol
     * @return A new aggregated callsite
     */
    AggregatedCallSite createCallSite(Object object);

}
