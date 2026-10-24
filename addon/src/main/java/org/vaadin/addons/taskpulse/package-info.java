/**
 * TaskPulse: run long work in the background from a Vaadin UI, and let users
 * follow it and get notified when it finishes, in every browser tab of their
 * session.
 * <p>
 * An {@link org.vaadin.addons.taskpulse.PulseAction Action} is a name and a
 * blocking mode. Starting it with its work adds a
 * {@link org.vaadin.addons.taskpulse.PulseTask Task} to the session's
 * {@link org.vaadin.addons.taskpulse.PulseTaskList Task list}; the work runs on
 * a virtual thread unless another executor is given, and reports through its
 * {@link org.vaadin.addons.taskpulse.PulseTaskContext}. The
 * {@link org.vaadin.addons.taskpulse.PulseCenter task center}, the
 * {@link org.vaadin.addons.taskpulse.PulseTray tray}, the toasts and the Blocking
 * Task dialog follow the Task's signals.
 *
 * <h2>Setup</h2>
 * Turn on server push, put the task center in the main layout, and set what
 * "Open" and "View all" do in this browser tab:
 * {@snippet :
 * @Push
 * public class Application implements AppShellConfigurator {
 * }
 *
 * public class MainLayout extends AppLayout {
 *     public MainLayout() {
 *         addToNavbar(new DrawerToggle(), new PulseCenter());
 *         PulseTab.current()
 *                 .setOpenHandler(task -> UI.getCurrent().navigate(TaskView.class,
 *                         new RouteParameters("taskId", String.valueOf(task.id()))))
 *                 .setViewAllHandler(() -> UI.getCurrent().navigate(TaskView.class));
 *     }
 * }
 * }
 *
 * <h2>Starting a Task</h2>
 * {@snippet :
 * static final PulseAction IMPORT_DATA = new PulseAction("Import data", BlockingMode.BLOCKING);
 *
 * static void importData(PulseTaskContext ctx) {
 *     for (int file = 1; file <= 8; file++) {
 *         ctx.setStatusText("Importing file " + file + " of 8");
 *         importFile(file);
 *         ctx.setProgress(file / 8.0);
 *     }
 *     ctx.setResultSummary("2,480 records");
 * }
 *
 * // In a view: a regular Button, disabled while the Action is Running
 * PulseTaskList taskList = PulseTaskList.getCurrent();
 * Button button = new Button("Start", e -> taskList.start(IMPORT_DATA, Imports::importData));
 * button.bindEnabled(Signal.not(taskList.isRunning(IMPORT_DATA)));
 * }
 *
 * <h2>Rules</h2>
 * <ul>
 * <li>An Action has at most one Running Task per session: starting it again
 * throws {@link java.lang.IllegalStateException}.</li>
 * <li>The work reports only through its context and holds no view or
 * component: a Retry, perhaps from another tab, runs the same work again.</li>
 * <li>Exceptions make the Task Failed; once the user cancels, the next report
 * throws {@link java.util.concurrent.CancellationException}. Work that reports
 * rarely checks {@link org.vaadin.addons.taskpulse.PulseTaskContext#isCancelled()}.</li>
 * <li>Work that must not run twice is marked
 * {@link org.vaadin.addons.taskpulse.PulseAction.Retry#NEVER}.</li>
 * <li>To change the clock or the executor of the whole session, install a
 * Task list before anything uses it, with
 * {@link org.vaadin.addons.taskpulse.PulseTaskList#install}.</li>
 * </ul>
 */
package org.vaadin.addons.taskpulse;
