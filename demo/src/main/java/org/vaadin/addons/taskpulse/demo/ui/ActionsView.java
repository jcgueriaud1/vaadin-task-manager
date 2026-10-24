package org.vaadin.addons.taskpulse.demo.ui;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

import org.springframework.beans.factory.annotation.Autowired;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.details.Details;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Pre;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.signals.Signal;
import org.vaadin.addons.taskpulse.PulseAction;
import org.vaadin.addons.taskpulse.PulseAction.BlockingMode;
import org.vaadin.addons.taskpulse.PulseTaskContext;
import org.vaadin.addons.taskpulse.PulseTaskList;

/**
 * The demo Actions, one card each with an explanation, the code that defines
 * the Action and its work, and a regular start button.
 */
@Route(value = "", layout = MainLayout.class)
@PageTitle("Actions")
public class ActionsView extends Div {

    static final PulseAction SEND_NEWSLETTER = new PulseAction("Send newsletter", BlockingMode.NON_BLOCKING);
    static final PulseAction GENERATE_REPORT = new PulseAction("Generate report", BlockingMode.NON_BLOCKING);
    static final PulseAction SYNC_WITH_SERVER = new PulseAction("Sync with server", BlockingMode.NON_BLOCKING);
    static final PulseAction IMPORT_DATA = new PulseAction("Import data", BlockingMode.BLOCKING);
    static final PulseAction EXPORT_CUSTOMERS = new PulseAction("Export customers", BlockingMode.NON_BLOCKING);

    // The work of each Action. It reports through its PulseTaskContext and never touches the UI

    // Without Progress: the tray shows a spinning ring
    static void sendNewsletter(PulseTaskContext ctx) {
        ctx.setStatusText("Preparing recipients");
        sleep(1500);
        for (int batch = 1; batch <= 5; batch++) {
            ctx.setStatusText("Sending emails: batch " + batch + " of 5");
            sleep(1000);
        }
        ctx.setStatusText("");
        ctx.setResultSummary("Sent to 1,240 subscribers");
    }

    // With Progress: the first setProgress(0..1) makes the Task determinate
    static void generateReport(PulseTaskContext ctx) {
        String[] steps = { "collecting data", "aggregating", "rendering charts", "writing PDF files" };
        for (int step = 0; step < steps.length; step++) {
            ctx.setStatusText("Step " + (step + 1) + " of " + steps.length + ": " + steps[step] + "…");
            for (int tick = 1; tick <= 10; tick++) {
                sleep(200);
                ctx.setProgress((step + tick / 10.0) / steps.length);
            }
        }
        ctx.setStatusText("");
        ctx.setResultSummary("5 PDF files");
    }

    // Fails half of the time: any exception the work throws makes the Task Failed
    static void syncWithServer(PulseTaskContext ctx) {
        ctx.setStatusText("Connecting");
        sleep(1500);
        ctx.setStatusText("Downloading changes");
        sleep(2000);
        if (ThreadLocalRandom.current().nextBoolean()) {
            throw new IllegalStateException("Server unreachable: connection timed out");
        }
        ctx.setStatusText("");
        ctx.setResultSummary("Up to date");
    }

    // Blocking: starting it from this view opens a PulseBlockingDialog until the Task finishes
    static void importData(PulseTaskContext ctx) {
        int files = 8;
        for (int file = 1; file <= files; file++) {
            ctx.setStatusText("Importing file " + file + " of " + files);
            sleep(600);
            ctx.setProgress((double) file / files);
        }
        ctx.setStatusText("");
        ctx.setResultSummary("2,480 records");
    }

    // With a Result file: the toast, the tray and the task center link to it, and it opens in a new tab
    static void exportCustomers(PulseTaskContext ctx) {
        int customers = 10;
        StringBuilder rows = new StringBuilder();
        for (int customer = 1; customer <= customers; customer++) {
            ctx.setStatusText("Exporting customer " + customer + " of " + customers);
            sleep(500);
            rows.append("<tr><td>").append(customer).append("</td><td>Customer ").append(customer)
                    .append("</td></tr>\n");
            ctx.setProgress((double) customer / customers);
        }
        try {
            // The demo only removes it on shutdown; an app would clean up old exports
            Path folder = Files.createTempDirectory("export-");
            folder.toFile().deleteOnExit();
            Path file = folder.resolve("customers.html");
            file.toFile().deleteOnExit();
            Files.writeString(file, "<!doctype html><title>Customers</title><table>\n" + rows + "</table>\n");
            ctx.setStatusText("");
            ctx.setResultSummary(customers + " customers");
            ctx.setResultFile(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** An Action and its work, as one card shows them. */
    record Demo(PulseAction action, Consumer<PulseTaskContext> work) {
    }

    private final PulseTaskList taskList;

    @Autowired
    public ActionsView(PulseTaskList taskList) {
        this.taskList = taskList;
        addClassName("actions-view");
        add(intro());
        // The code shown on each card mirrors the constants and methods above; keep them in sync
        add(card(new Demo(SEND_NEWSLETTER, ActionsView::sendNewsletter), "Non-blocking, without Progress",
                """
                The simplest kind. Its work updates the Status text and ends with a \
                Result summary. It never reports Progress, so the tray shows a \
                spinning ring. Start it, then keep using the app or cancel it \
                from the tray.""",
                """
                static final PulseAction SEND_NEWSLETTER =
                        new PulseAction("Send newsletter", BlockingMode.NON_BLOCKING);

                static void sendNewsletter(PulseTaskContext ctx) {
                    ctx.setStatusText("Preparing recipients");
                    sleep(1500);
                    for (int batch = 1; batch <= 5; batch++) {
                        ctx.setStatusText("Sending emails: batch " + batch + " of 5");
                        sleep(1000);
                    }
                    ctx.setStatusText("");
                    ctx.setResultSummary("Sent to 1,240 subscribers");
                }

                // In the view: a regular button
                add(startButton(SEND_NEWSLETTER, ActionsView::sendNewsletter));"""));
        add(card(new Demo(GENERATE_REPORT, ActionsView::generateReport), "Non-blocking, with Progress",
                """
                Its work also calls setProgress(0..1), so the tray and the navbar \
                show how much is done.""",
                """
                static final PulseAction GENERATE_REPORT =
                        new PulseAction("Generate report", BlockingMode.NON_BLOCKING);

                static void generateReport(PulseTaskContext ctx) {
                    String[] steps = { "collecting data", "aggregating",
                            "rendering charts", "writing PDF files" };
                    for (int step = 0; step < steps.length; step++) {
                        ctx.setStatusText("Step " + (step + 1) + " of "
                                + steps.length + ": " + steps[step] + "…");
                        for (int tick = 1; tick <= 10; tick++) {
                            sleep(200);
                            ctx.setProgress((step + tick / 10.0) / steps.length);
                        }
                    }
                    ctx.setStatusText("");
                    ctx.setResultSummary("5 PDF files");
                }

                // In the view: a regular button
                add(startButton(GENERATE_REPORT, ActionsView::generateReport));"""));
        add(card(new Demo(SYNC_WITH_SERVER, ActionsView::syncWithServer), "Non-blocking, fails at random",
                """
                Any exception thrown by the work makes the Task Failed, with the \
                exception message as its error. Open the failure toast or the \
                Background tasks page to Retry it.""",
                """
                static final PulseAction SYNC_WITH_SERVER =
                        new PulseAction("Sync with server", BlockingMode.NON_BLOCKING);

                static void syncWithServer(PulseTaskContext ctx) {
                    ctx.setStatusText("Connecting");
                    sleep(1500);
                    ctx.setStatusText("Downloading changes");
                    sleep(2000);
                    if (ThreadLocalRandom.current().nextBoolean()) {
                        throw new IllegalStateException(
                                "Server unreachable: connection timed out");
                    }
                    ctx.setStatusText("");
                    ctx.setResultSummary("Up to date");
                }

                // In the view: a regular button
                add(startButton(SYNC_WITH_SERVER, ActionsView::syncWithServer));"""));
        add(card(new Demo(IMPORT_DATA, ActionsView::importData), "Blocking, with Progress",
                """
                BlockingMode.BLOCKING: starting it opens a dialog over this tab \
                until the Task finishes or is cancelled, and so does Retry. Other tabs of the session stay usable \
                and see the Task in their tray.""",
                """
                static final PulseAction IMPORT_DATA =
                        new PulseAction("Import data", BlockingMode.BLOCKING);

                static void importData(PulseTaskContext ctx) {
                    int files = 8;
                    for (int file = 1; file <= files; file++) {
                        ctx.setStatusText("Importing file " + file + " of " + files);
                        sleep(600);
                        ctx.setProgress((double) file / files);
                    }
                    ctx.setStatusText("");
                    ctx.setResultSummary("2,480 records");
                }

                // In the view: a regular button, which also opens the dialog
                add(startButton(IMPORT_DATA, ActionsView::importData));"""));
        add(card(new Demo(EXPORT_CUSTOMERS, ActionsView::exportCustomers), "Non-blocking, with a Result file",
                """
                Its work writes a file slowly, then hands it over with setResultFile. \
                Once it Succeeds, the toast, the tray and the task center show a link \
                that opens the file in a new browser tab.""",
                """
                static final PulseAction EXPORT_CUSTOMERS =
                        new PulseAction("Export customers", BlockingMode.NON_BLOCKING);

                static void exportCustomers(PulseTaskContext ctx) {
                    int customers = 10;
                    StringBuilder rows = new StringBuilder();
                    for (int customer = 1; customer <= customers; customer++) {
                        ctx.setStatusText("Exporting customer " + customer
                                + " of " + customers);
                        sleep(500);
                        rows.append("<tr><td>").append(customer)
                                .append("</td><td>Customer ").append(customer)
                                .append("</td></tr>\\n");
                        ctx.setProgress((double) customer / customers);
                    }
                    try {
                        // The demo only removes it on shutdown;
                        // an app would clean up old exports
                        Path folder = Files.createTempDirectory("export-");
                        folder.toFile().deleteOnExit();
                        Path file = folder.resolve("customers.html");
                        file.toFile().deleteOnExit();
                        Files.writeString(file, "<!doctype html><title>Customers</title>"
                                + "<table>\\n" + rows + "</table>\\n");
                        ctx.setStatusText("");
                        ctx.setResultSummary(customers + " customers");
                        ctx.setResultFile(file);
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                }

                // In the view: a regular button
                add(startButton(EXPORT_CUSTOMERS, ActionsView::exportCustomers));"""));
    }

    /** Shows the given Actions instead of the demo ones, without explanations. For tests. */
    ActionsView(PulseTaskList taskList, List<Demo> demos) {
        this.taskList = taskList;
        addClassName("actions-view");
        demos.forEach(demo -> add(card(demo, "", "", "")));
    }

    /**
     * A regular button that starts a Task of the Action. While the Action has a
     * Running Task, in any tab of the session, it says so and is disabled.
     */
    private Button startButton(PulseAction action, Consumer<PulseTaskContext> work) {
        Button button = new Button("Start", e -> taskList.start(action, work));
        Signal<Boolean> running = taskList.isRunning(action);
        button.bindText(running.map(r -> r ? action.name() + " running…" : "Start"));
        button.bindEnabled(Signal.not(running));
        button.setAriaLabel("Start " + action.name());
        return button;
    }

    private static Div intro() {
        Div intro = new Div(
                new H2("TaskPulse"),
                new Paragraph("""
                        Each card below is an Action, a name and a blocking mode, with the work it does. \
                        Its start button is a regular Button: the view starts a Task in the Task list with \
                        its work, which runs on the Task list's executor, here virtual threads. The work reports back \
                        through the Task's signals, and the tray, the navbar, the dialog and every open \
                        tab of your session follow along."""),
                new Paragraph("""
                        Try this: start a Task, then open this page in a second tab or switch to another \
                        tab before it finishes. You get a toast when it ends, or a native notification \
                        and a count in the page title if no tab was visible."""),
                code("Show how the buttons start Tasks", """
                        // The work runs on the Task list's executor: here virtual threads.
                        // A Blocking Action covers this tab until its Task finishes; Retry too.
                        Button button = new Button("Start", e -> taskList.start(action, work));
                        // Locked while the Action runs, in any tab of the session
                        Signal<Boolean> running = taskList.isRunning(action);
                        button.bindText(running.map(r -> r ? action.name() + " running…" : "Start"));
                        button.bindEnabled(Signal.not(running));"""));
        intro.addClassName("actions-intro");
        return intro;
    }

    private Div card(Demo demo, String kind, String explanation, String code) {
        Button start = startButton(demo.action(), demo.work());
        start.addThemeVariants(ButtonVariant.PRIMARY);
        Div card = new Div(new H2(demo.action().name()));
        if (!kind.isEmpty()) {
            Span kindLabel = new Span(kind);
            kindLabel.addClassName("action-kind");
            card.add(kindLabel, new Paragraph(explanation));
        }
        card.add(start);
        if (!code.isEmpty()) {
            card.add(code("Show the code", code));
        }
        card.addClassName("action-card");
        return card;
    }

    private static Details code(String summary, String code) {
        Pre pre = new Pre(code);
        pre.addClassName("action-code");
        Details details = new Details(summary, pre);
        details.addClassName("action-code-details");
        return details;
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
