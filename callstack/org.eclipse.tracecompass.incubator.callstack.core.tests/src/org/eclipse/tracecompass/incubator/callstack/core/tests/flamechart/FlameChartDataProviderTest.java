/*******************************************************************************
 * Copyright (c) 2018 École Polytechnique de Montréal
 *
 * All rights reserved. This program and the accompanying materials are
 * made available under the terms of the Eclipse Public License v1.0 which
 * accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/

package org.eclipse.tracecompass.incubator.callstack.core.tests.flamechart;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.tracecompass.incubator.callstack.core.tests.stubs.CallStackAnalysisStub;
import org.eclipse.tracecompass.incubator.internal.callstack.core.instrumented.provider.FlameChartDataProvider;
import org.eclipse.tracecompass.incubator.internal.callstack.core.instrumented.provider.FlameChartDataProviderFactory;
import org.eclipse.tracecompass.incubator.internal.callstack.core.instrumented.provider.FlameChartEntryModel;
import org.eclipse.tracecompass.incubator.internal.callstack.core.instrumented.provider.FlameChartEntryModel.EntryType;
import org.eclipse.tracecompass.internal.provisional.tmf.core.model.filters.SelectionTimeQueryFilter;
import org.eclipse.tracecompass.internal.provisional.tmf.core.model.filters.TimeQueryFilter;
import org.eclipse.tracecompass.internal.provisional.tmf.core.model.timegraph.ITimeGraphRowModel;
import org.eclipse.tracecompass.internal.provisional.tmf.core.model.timegraph.ITimeGraphState;
import org.eclipse.tracecompass.internal.provisional.tmf.core.model.timegraph.TimeGraphState;
import org.eclipse.tracecompass.internal.provisional.tmf.core.response.ITmfResponse;
import org.eclipse.tracecompass.internal.provisional.tmf.core.response.TmfModelResponse;
import org.junit.Test;

import com.google.common.collect.ImmutableList;

/**
 * Test the {@link FlameChartDataProvider} class
 *
 * @author Geneviève Bastien
 */
@SuppressWarnings("restriction")
public class FlameChartDataProviderTest extends CallStackTestBase {

    private FlameChartDataProvider getDataProvider() {
        CallStackAnalysisStub module = getModule();
        assertNotNull(module);

        FlameChartDataProviderFactory factory = new FlameChartDataProviderFactory();

        FlameChartDataProvider dataProvider = (FlameChartDataProvider) factory.createProvider(getTrace(), module.getId());
        assertNotNull(dataProvider);
        return dataProvider;
    }

    /**
     * Test getting the tree from the flame chart data provider
     */
    @Test
    public void testFetchTree() {
        FlameChartDataProvider dataProvider = getDataProvider();

        TmfModelResponse<List<FlameChartEntryModel>> responseTree = dataProvider.fetchTree(new TimeQueryFilter(0, Long.MAX_VALUE, 2), new NullProgressMonitor());
        assertTrue(responseTree.getStatus().equals(ITmfResponse.Status.COMPLETED));

        // Test the size of the tree
        List<FlameChartEntryModel> model = responseTree.getModel();
        assertNotNull(model);
        assertEquals(18, model.size());

        String traceName = getTrace().getName();

        // Test the hierarchy of the tree
        for (FlameChartEntryModel entry : model) {
            FlameChartEntryModel parent = findEntryById(model, entry.getParentId());
            switch (entry.getEntryType()) {
            case FUNCTION:
                assertNotNull(parent);
                assertEquals(EntryType.LEVEL, parent.getEntryType());
                break;
            case LEVEL: {
                assertNotNull(parent);
                // Verify the hierarchy of the elements
                switch (entry.getName()) {
                case "1":
                    assertEquals(traceName, parent.getName());
                    break;
                case "2":
                    assertEquals("1", parent.getName());
                    break;
                case "3":
                    assertEquals("1", parent.getName());
                    break;
                case "5":
                    assertEquals(traceName, parent.getName());
                    break;
                case "6":
                    assertEquals("5", parent.getName());
                    break;
                case "7":
                    assertEquals("5", parent.getName());
                    break;
                default:
                    fail("Unknown entry " + entry.getName());
                    break;
                }
            }
                break;
            case KERNEL:
                fail("There should be no kernel entry in this callstack");
                break;
            case TRACE:
                assertEquals(-1, entry.getParentId());
                break;
            default:
                fail("Unknown entry " + entry);
                break;
            }
        }
    }

    private static @Nullable FlameChartEntryModel findEntryById(Collection<FlameChartEntryModel> list, long id) {
        return list.stream()
                .filter(entry -> entry.getId() == id)
                .findFirst().orElse(null);
    }

    private static @Nullable FlameChartEntryModel findEntryByNameAndType(Collection<FlameChartEntryModel> list, String name, EntryType type) {
        return list.stream()
                .filter(entry -> entry.getEntryType().equals(type) && entry.getName().equals(name))
                .findFirst().orElse(null);
    }

    private static List<FlameChartEntryModel> findEntriesByParent(Collection<FlameChartEntryModel> list, long parentId) {
        return list.stream()
                .filter(entry -> entry.getParentId() == parentId)
                .collect(Collectors.toList());
    }

    /**
     * Test getting the model from the flame chart data provider
     */
    @Test
    public void testFetchModel() {
        FlameChartDataProvider dataProvider = getDataProvider();

        TmfModelResponse<List<FlameChartEntryModel>> responseTree = dataProvider.fetchTree(new TimeQueryFilter(0, Long.MAX_VALUE, 2), new NullProgressMonitor());
        assertTrue(responseTree.getStatus().equals(ITmfResponse.Status.COMPLETED));
        List<FlameChartEntryModel> model = responseTree.getModel();

        // Find the entries corresponding to threads 3 and 6 (along with pid 5)
        Set<@NonNull Long> selectedIds = new HashSet<>();
        // Thread 3
        FlameChartEntryModel tid3 = findEntryByNameAndType(model, "3", EntryType.LEVEL);
        assertNotNull(tid3);
        selectedIds.add(tid3.getId());
        List<FlameChartEntryModel> tid3Children = findEntriesByParent(model, tid3.getId());
        assertEquals(2, tid3Children.size());
        tid3Children.forEach(child -> selectedIds.add(child.getId()));
        // Pid 5
        FlameChartEntryModel pid5 = findEntryByNameAndType(model, "5", EntryType.LEVEL);
        assertNotNull(pid5);
        selectedIds.add(pid5.getId());
        // Thread 6
        FlameChartEntryModel tid6 = findEntryByNameAndType(model, "6", EntryType.LEVEL);
        assertNotNull(tid6);
        selectedIds.add(tid6.getId());
        List<FlameChartEntryModel> tid6Children = findEntriesByParent(model, tid6.getId());
        assertEquals(3, tid6Children.size());
        tid6Children.forEach(child -> selectedIds.add(child.getId()));

        // Get the row model for those entries with high resolution
        TmfModelResponse<List<ITimeGraphRowModel>> rowModel = dataProvider.fetchRowModel(new SelectionTimeQueryFilter(3, 15, 50, selectedIds), new NullProgressMonitor());
        assertEquals(ITmfResponse.Status.COMPLETED, rowModel.getStatus());

        List<ITimeGraphRowModel> rowModels = rowModel.getModel();
        assertNotNull(rowModels);
        assertEquals(8, rowModels.size());

        // Verify the level entries
        verifyStates(rowModels, tid3, Collections.emptyList());
        verifyStates(rowModels, pid5, Collections.emptyList());
        verifyStates(rowModels, tid6, Collections.emptyList());
        // Verify function level 1 of tid 3
        verifyStates(rowModels, findEntryByNameAndType(tid3Children, "1", EntryType.FUNCTION), ImmutableList.of(new TimeGraphState(3, 17, Long.MIN_VALUE, "op2")));
        // Verify function level 2 of tid 3
        verifyStates(rowModels, findEntryByNameAndType(tid3Children, "2", EntryType.FUNCTION), ImmutableList.of(
                new TimeGraphState(1, 4, Long.MIN_VALUE),
                new TimeGraphState(5, 1, Long.MIN_VALUE, "op3"),
                new TimeGraphState(6, 1, Long.MIN_VALUE),
                new TimeGraphState(7, 6, Long.MIN_VALUE, "op2"),
                new TimeGraphState(13, 8, Long.MIN_VALUE)));
        // Verify function level 1 of tid 6
        verifyStates(rowModels, findEntryByNameAndType(tid6Children, "1", EntryType.FUNCTION), ImmutableList.of(new TimeGraphState(1, 19, Long.MIN_VALUE, "op1")));
        // Verify function level 2 of tid 6
        verifyStates(rowModels, findEntryByNameAndType(tid6Children, "2", EntryType.FUNCTION), ImmutableList.of(
                new TimeGraphState(2, 5, Long.MIN_VALUE, "op3"),
                new TimeGraphState(7, 1, Long.MIN_VALUE),
                new TimeGraphState(8, 3, Long.MIN_VALUE, "op2"),
                new TimeGraphState(11, 1, Long.MIN_VALUE),
                new TimeGraphState(12, 8, Long.MIN_VALUE, "op4")));
        // Verify function level 3 of tid 6
        verifyStates(rowModels, findEntryByNameAndType(tid6Children, "3", EntryType.FUNCTION), ImmutableList.of(
                new TimeGraphState(1, 3, Long.MIN_VALUE),
                new TimeGraphState(4, 2, Long.MIN_VALUE, "op1"),
                new TimeGraphState(6, 3, Long.MIN_VALUE),
                new TimeGraphState(9, 1, Long.MIN_VALUE, "op3"),
                new TimeGraphState(10, 11, Long.MIN_VALUE)));

        // Get the row model for those entries with low resolution
        rowModel = dataProvider.fetchRowModel(new SelectionTimeQueryFilter(3, 15, 2, selectedIds), new NullProgressMonitor());
        assertEquals(ITmfResponse.Status.COMPLETED, rowModel.getStatus());

        rowModels = rowModel.getModel();
        assertNotNull(rowModels);
        assertEquals(8, rowModels.size());

        // Verify the level entries
        verifyStates(rowModels, tid3, Collections.emptyList());
        verifyStates(rowModels, pid5, Collections.emptyList());
        verifyStates(rowModels, tid6, Collections.emptyList());
        // Verify function level 1 of tid 3
        verifyStates(rowModels, findEntryByNameAndType(tid3Children, "1", EntryType.FUNCTION), ImmutableList.of(new TimeGraphState(3, 17, Long.MIN_VALUE, "op2")));
        // Verify function level 2 of tid 3
        verifyStates(rowModels, findEntryByNameAndType(tid3Children, "2", EntryType.FUNCTION), ImmutableList.of(
                new TimeGraphState(1, 4, Long.MIN_VALUE),
                new TimeGraphState(13, 8, Long.MIN_VALUE)));
        // Verify function level 1 of tid 6
        verifyStates(rowModels, findEntryByNameAndType(tid6Children, "1", EntryType.FUNCTION), ImmutableList.of(new TimeGraphState(1, 19, Long.MIN_VALUE, "op1")));
        // Verify function level 2 of tid 6
        verifyStates(rowModels, findEntryByNameAndType(tid6Children, "2", EntryType.FUNCTION), ImmutableList.of(
                new TimeGraphState(2, 5, Long.MIN_VALUE, "op3"),
                new TimeGraphState(12, 8, Long.MIN_VALUE, "op4")));
        // Verify function level 3 of tid 6
        verifyStates(rowModels, findEntryByNameAndType(tid6Children, "3", EntryType.FUNCTION), ImmutableList.of(
                new TimeGraphState(1, 3, Long.MIN_VALUE),
                new TimeGraphState(10, 11, Long.MIN_VALUE)));
    }

    private static void verifyStates(List<ITimeGraphRowModel> rowModels, FlameChartEntryModel entry, List<TimeGraphState> expectedStates) {
        assertNotNull(entry);
        ITimeGraphRowModel rowModel = rowModels.stream()
                .filter(model -> model.getEntryID() == entry.getId())
                .findFirst().orElse(null);
        assertNotNull(rowModel);
        List<ITimeGraphState> states = rowModel.getStates();
        for (int i = 0; i < states.size(); i++) {
            if (i > expectedStates.size() - 1) {
                fail("Unexpected state at position " + i + " for entry " + entry.getName() + ": " + states.get(i));
            }
            ITimeGraphState actual = states.get(i);
            ITimeGraphState expected = expectedStates.get(i);
            assertEquals("State start time at " + i + " for entry " + entry.getName(), expected.getStartTime(), actual.getStartTime());
            assertEquals("Duration at " + i + " for entry " + entry.getName(), expected.getDuration(), actual.getDuration());
            assertEquals("Label at " + i + " for entry " + entry.getName(), expected.getLabel(), actual.getLabel());

        }
    }

}
