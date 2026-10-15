package com.example.taskmanager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.example.taskmanager.task.AsyncAction;
import com.example.taskmanager.task.BlockingMode;

class DemoActionsTest {

    @Test
    void theActionsPageOffersGenerateReportAsANonBlockingActionWithProgress() {
        AsyncAction<?> report = new TaskConfig().demoActions().actions().stream()
                .filter(action -> action.name().equals("Generate report"))
                .findFirst().orElseThrow();

        assertEquals(BlockingMode.NON_BLOCKING, report.blockingMode());
        assertTrue(report.reportsProgress());
    }

    @Test
    void theActionsPageOffersImportDataAsABlockingActionWithProgress() {
        AsyncAction<?> importData = new TaskConfig().demoActions().actions().stream()
                .filter(action -> action.name().equals("Import data"))
                .findFirst().orElseThrow();

        assertEquals(BlockingMode.BLOCKING, importData.blockingMode());
        assertTrue(importData.reportsProgress());
    }
}
