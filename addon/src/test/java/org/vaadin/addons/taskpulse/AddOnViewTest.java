package org.vaadin.addons.taskpulse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.vaadin.browserless.BrowserlessTest;
import com.vaadin.browserless.ViewPackages;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.VaadinSession;
import org.vaadin.addons.taskpulse.PulseAction.BlockingMode;

/** The add-on's components in a plain Vaadin app, without Spring: what a user gets by default. */
@ViewPackages(classes = AddOnViewTest.WatchedView.class)
class AddOnViewTest extends BrowserlessTest {

    private static final PulseAction EXPORT = new PulseAction("Export data", BlockingMode.NON_BLOCKING);

    /** A view with the tray and the task center of the session's Task list, watched for its tab. */
    @Route("watched")
    @PageTitle("Watched")
    public static class WatchedView extends Div {

        public WatchedView() {
            add(new PulseCenter(), new PulseTray());
            PulseWatcher.watch(this);
        }
    }

    @BeforeEach
    void open() {
        navigate(WatchedView.class);
    }

    @Test
    void theSessionHasOneTaskListThatTheComponentsShare() {
        PulseTaskList taskList = PulseTaskList.getCurrent();
        assertSame(taskList, PulseTaskList.get(VaadinSession.getCurrent()));

        taskList.start(EXPORT, Runnable::run, ctx -> ctx.setResultSummary("3 CSV files"));
        runPendingSignalsTasks();

        String tray = $(PulseTray.class).single().getElement().getTextRecursively();
        assertTrue(tray.contains("Export data"), tray);
        assertTrue(tray.contains("3 CSV files"), tray);
    }

    @Test
    void aTaskListCanOnlyBeInstalledBeforeTheSessionHasOne() {
        VaadinSession session = VaadinSession.getCurrent();

        assertThrows(IllegalStateException.class, () -> PulseTaskList.install(session, new PulseTaskList()));
    }

    @Test
    void withoutAnOpenHandlerThereIsNoOpenButton() {
        PulseTaskList.getCurrent().start(EXPORT, Runnable::run, ctx -> {});
        runPendingSignalsTasks();

        assertFalse($(Button.class).from($(PulseTray.class).single()).withText("Open").exists());
    }

    @Test
    void openHandsTheTaskToTheHandler() {
        PulseTask task = PulseTaskList.getCurrent().start(EXPORT, Runnable::run, ctx -> {});
        runPendingSignalsTasks();
        List<PulseTask> opened = new ArrayList<>();
        $(PulseTray.class).single().setOpenHandler(opened::add);
        runPendingSignalsTasks();

        test($(Button.class).from($(PulseTray.class).single()).withText("Open").single()).click();

        assertEquals(List.of(task), opened);
    }

    @Test
    void thePageTitleStartsWithTheOverallProgress() {
        TestExecutor executor = new TestExecutor();
        PulseTaskList.getCurrent().start(EXPORT, executor, ctx -> {
            ctx.setProgress(0.5);
            executor.pause();
        });
        executor.runNext();
        runPendingSignalsTasks();
        roundTrip();

        assertEquals("50% · Watched", UI.getCurrent().getInternals().getTitle());

        executor.resume();
    }
}
