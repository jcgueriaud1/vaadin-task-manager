package org.vaadin.addons.taskpulse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.vaadin.browserless.BrowserlessTest;
import com.vaadin.browserless.ViewPackages;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.popover.Popover;
import com.vaadin.flow.component.popover.PopoverTester;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.VaadinSession;
import org.vaadin.addons.taskpulse.PulseAction.BlockingMode;

/** The add-on in a plain Vaadin app, without Spring: what a user gets by default. */
@ViewPackages(classes = { AddOnViewTest.CenterView.class, AddOnViewTest.PlainView.class })
class AddOnViewTest extends BrowserlessTest {

    private static final PulseAction EXPORT = new PulseAction("Export data", BlockingMode.NON_BLOCKING);

    /** The one line an app needs: a task center. */
    @Route("center")
    @PageTitle("Orders")
    public static class CenterView extends Div {

        public CenterView() {
            add(new PulseCenter());
        }
    }

    /** A view with no TaskPulse component. */
    @Route("plain")
    @PageTitle("Plain")
    public static class PlainView extends Div {
    }

    @Test
    void aTaskCenterSetsUpTheTabWithItsTray() {
        navigate(CenterView.class);
        PulseTask task = PulseTab.current().taskList().start(EXPORT, Runnable::run,
                ctx -> ctx.setResultSummary("3 CSV files"));
        runPendingSignalsTasks();

        assertSame(PulseTaskList.getCurrent(), PulseTab.current().taskList());
        String tray = $(PulseTray.class).single().getElement().getTextRecursively();
        assertTrue(tray.contains("Export data"), tray);
        assertTrue(tray.contains("3 CSV files"), tray);
        assertFalse(task.unseen().peek(), "the tab is tracked and visible");
    }

    @Test
    void theTabIsSetUpOncePerUi() {
        navigate(CenterView.class);
        PulseTab first = PulseTab.current();

        navigate(PlainView.class);
        navigate(CenterView.class);

        assertSame(first, PulseTab.current());
        assertEquals(1, UI.getCurrent().getChildren().filter(PulseTray.class::isInstance).count());
    }

    @Test
    void aTaskListCanOnlyBeInstalledBeforeTheSessionHasOne() {
        navigate(CenterView.class);
        VaadinSession session = VaadinSession.getCurrent();

        assertThrows(IllegalStateException.class, () -> PulseTaskList.install(session, new PulseTaskList()));
    }

    @Test
    void withoutAnOpenHandlerThereIsNoOpenButton() {
        navigate(CenterView.class);
        PulseTab.current().taskList().start(EXPORT, Runnable::run, ctx -> {});
        runPendingSignalsTasks();

        assertFalse($(Button.class).from($(PulseTray.class).single()).withText("Open").exists());
        assertFalse($(Button.class).from(openPopover()).withText("Open").exists());
        assertFalse($(Button.class).from(openPopover()).withText("View all").exists());
    }

    @Test
    void oneOpenHandlerServesTheTrayAndTheTaskCenter() {
        navigate(CenterView.class);
        List<PulseTask> opened = new ArrayList<>();
        List<String> viewedAll = new ArrayList<>();
        PulseTab.current().setOpenHandler(opened::add).setViewAllHandler(() -> viewedAll.add("all"));
        PulseTask task = PulseTab.current().taskList().start(EXPORT, Runnable::run, ctx -> {});
        runPendingSignalsTasks();

        test($(Button.class).from($(PulseTray.class).single()).withText("Open").single()).click();
        test($(Button.class).from(openPopover()).withText("Open").single()).click();
        test($(Button.class).from(openPopover()).withText("View all").single()).click();

        assertEquals(List.of(task, task), opened);
        assertEquals(List.of("all"), viewedAll);
    }

    @Test
    void theTrayCanBeTurnedOff() {
        navigate(CenterView.class);
        PulseTab.current().setTrayEnabled(false);
        PulseTab.current().taskList().start(EXPORT, Runnable::run, ctx -> {});
        runPendingSignalsTasks();

        assertFalse($(PulseTray.class).exists());
    }

    @Test
    void thePageTitleStartsWithTheOverallProgressUnlessTurnedOff() {
        navigate(CenterView.class);
        TestExecutor executor = new TestExecutor();
        PulseTab.current().taskList().start(EXPORT, executor, ctx -> {
            ctx.setProgress(0.5);
            executor.pause();
        });
        executor.runNext();
        runPendingSignalsTasks();
        roundTrip();
        assertEquals("50% · Orders", UI.getCurrent().getInternals().getTitle());

        PulseTab.current().setPageTitleUpdates(false);
        navigate(PlainView.class);
        roundTrip();
        assertEquals("Plain", UI.getCurrent().getInternals().getTitle());

        executor.resume();
    }

    @Test
    void aViewWithoutTaskPulseComponentsSetsUpNothing() {
        navigate(PlainView.class);
        PulseTask task = PulseTaskList.getCurrent().start(EXPORT, Runnable::run, ctx -> {});

        assertFalse($(PulseTray.class).exists());
        assertFalse(task.unseen().peek(), "no tab is tracked, so nobody missed it");
    }

    @Test
    void aBlockingTaskStartedFromATabCoversItAndSoDoesItsRetry() {
        navigate(CenterView.class);
        PulseAction restore = new PulseAction("Restore backup", BlockingMode.BLOCKING);
        TestExecutor executor = new TestExecutor();
        PulseTask task = PulseTab.current().taskList().start(restore, executor, ctx -> {
            executor.pause();
            throw new IllegalStateException("Backup is corrupt");
        });
        assertTrue($(PulseBlockingDialog.class).exists());

        executor.runAll();
        runPendingSignalsTasks();
        assertFalse($(PulseBlockingDialog.class).exists());

        PulseTab.current().taskList().retry(task);
        assertTrue($(PulseBlockingDialog.class).exists(), "the Retry covers the tab it came from");
        executor.runAll();
    }

    @Test
    void aBlockingTaskStartedWithoutATabBlocksNothing() {
        navigate(CenterView.class);
        PulseTaskList taskList = PulseTab.current().taskList();
        UI tab = UI.getCurrent();
        UI.setCurrent(null);
        taskList.start(new PulseAction("Restore backup", BlockingMode.BLOCKING), new TestExecutor(), ctx -> {});
        UI.setCurrent(tab);

        assertFalse($(PulseBlockingDialog.class).exists());
    }

    private Popover openPopover() {
        PopoverTester tester = PopoverTester.forTarget($(Button.class).withAriaLabel("Task center").single());
        if (!tester.isOpen()) {
            tester.clickTarget();
        }
        runPendingSignalsTasks();
        return tester.getComponent();
    }
}
