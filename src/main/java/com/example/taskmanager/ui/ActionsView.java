package com.example.taskmanager.ui;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.beans.factory.annotation.Autowired;

import com.example.taskmanager.task.AsyncAction;
import com.example.taskmanager.task.AsyncProgressAction;
import com.example.taskmanager.task.BlockingMode;
import com.example.taskmanager.task.Task;
import com.example.taskmanager.task.TaskList;
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

/**
 * The demo Actions, one card each with an explanation, the code that defines
 * the Action and a start button.
 * <p>
 * The Actions are constants: {@link TaskList} recognises an Action by identity,
 * so every tab and every visit of this view must share the same instances.
 */
@Route(value = "", layout = MainLayout.class)
@PageTitle("Actions")
public class ActionsView extends Div {

    // Without Progress: the work function gets a TaskContext (status text, result summary)
    static final AsyncAction<Integer> SEND_NEWSLETTER = new AsyncAction<>(
            "Send newsletter", BlockingMode.NON_BLOCKING, ctx -> {
                ctx.setStatusText("Preparing recipients");
                sleep(1500);
                for (int batch = 1; batch <= 5; batch++) {
                    ctx.setStatusText("Sending emails: batch " + batch + " of 5");
                    sleep(1000);
                }
                ctx.setStatusText("");
                ctx.setResultSummary("Sent to 1,240 subscribers");
                return 1240;
            });

    // With Progress: the work function gets a ProgressContext, which adds setProgress(0..1)
    static final AsyncProgressAction<Integer> GENERATE_REPORT = new AsyncProgressAction<>(
            "Generate report", BlockingMode.NON_BLOCKING, ctx -> {
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
                return 5;
            });

    // Fails half of the time: any exception the work throws makes the Task Failed
    static final AsyncAction<Void> SYNC_WITH_SERVER = new AsyncAction<>(
            "Sync with server", BlockingMode.NON_BLOCKING, ctx -> {
                ctx.setStatusText("Connecting");
                sleep(1500);
                ctx.setStatusText("Downloading changes");
                sleep(2000);
                if (ThreadLocalRandom.current().nextBoolean()) {
                    throw new IllegalStateException("Server unreachable: connection timed out");
                }
                ctx.setStatusText("");
                ctx.setResultSummary("Up to date");
                return null;
            });

    // Blocking: the view opens a BlockingTaskDialog over its tab until the Task finishes
    static final AsyncProgressAction<Integer> IMPORT_DATA = new AsyncProgressAction<>(
            "Import data", BlockingMode.BLOCKING, ctx -> {
                int files = 8;
                for (int file = 1; file <= files; file++) {
                    ctx.setStatusText("Importing file " + file + " of " + files);
                    sleep(600);
                    ctx.setProgress((double) file / files);
                }
                ctx.setStatusText("");
                ctx.setResultSummary("2,480 records");
                return 2480;
            });

    private final TaskList taskList;

    @Autowired
    public ActionsView(TaskList taskList) {
        this.taskList = taskList;
        addClassName("actions-view");
        add(intro());
        // The code shown on each card mirrors the constant above; keep them in sync
        add(card(SEND_NEWSLETTER, "Non-blocking, without Progress",
                """
                The simplest kind: an AsyncAction. Its work updates the Status text \
                and ends with a Result summary. Without Progress, the tray shows a \
                spinning ring. Start it, then keep using the app or cancel it \
                from the tray.""",
                """
                new AsyncAction<>("Send newsletter", BlockingMode.NON_BLOCKING, ctx -> {
                    ctx.setStatusText("Preparing recipients");
                    sleep(1500);
                    for (int batch = 1; batch <= 5; batch++) {
                        ctx.setStatusText("Sending emails: batch " + batch + " of 5");
                        sleep(1000);
                    }
                    ctx.setStatusText("");
                    ctx.setResultSummary("Sent to 1,240 subscribers");
                    return 1240;
                });"""));
        add(card(GENERATE_REPORT, "Non-blocking, with Progress",
                """
                An AsyncProgressAction: its work also gets setProgress(0..1), so the \
                tray and the navbar show how much is done.""",
                """
                new AsyncProgressAction<>("Generate report", BlockingMode.NON_BLOCKING, ctx -> {
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
                    return 5;
                });"""));
        add(card(SYNC_WITH_SERVER, "Non-blocking, fails at random",
                """
                Any exception thrown by the work makes the Task Failed, with the \
                exception message as its error. Open the failure toast or the \
                Background tasks page to Retry it.""",
                """
                new AsyncAction<>("Sync with server", BlockingMode.NON_BLOCKING, ctx -> {
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
                    return null;
                });"""));
        add(card(IMPORT_DATA, "Blocking, with Progress",
                """
                BlockingMode.BLOCKING: this view opens a dialog over its tab until the \
                Task finishes or is cancelled. Other tabs of the session stay usable \
                and see the Task in their tray.""",
                """
                new AsyncProgressAction<>("Import data", BlockingMode.BLOCKING, ctx -> {
                    int files = 8;
                    for (int file = 1; file <= files; file++) {
                        ctx.setStatusText("Importing file " + file + " of " + files);
                        sleep(600);
                        ctx.setProgress((double) file / files);
                    }
                    ctx.setStatusText("");
                    ctx.setResultSummary("2,480 records");
                    return 2480;
                });

                // On click
                Task task = action.startTask(taskList);
                new BlockingTaskDialog(task).open();"""));
    }

    /** Shows the given Actions instead of the demo ones, without explanations. For tests. */
    ActionsView(TaskList taskList, List<AsyncAction<?>> actions) {
        this.taskList = taskList;
        addClassName("actions-view");
        actions.forEach(action -> add(card(action, "", "", "")));
    }

    private static Div intro() {
        Div intro = new Div(
                new H2("Async Actions"),
                new Paragraph("""
                        Each card below is an Action: a name, a blocking mode and a work function. \
                        Starting it runs a Task on its own virtual thread. The work reports back through \
                        its context, and the tray, the navbar and every open tab of your session follow \
                        along."""),
                new Paragraph("""
                        Try this: start a Task, then open this page in a second tab or switch to another \
                        tab before it finishes. You get a toast when it ends, or a native notification \
                        and a count in the page title if no tab was visible."""));
        intro.addClassName("actions-intro");
        return intro;
    }

    private Div card(AsyncAction<?> action, String kind, String explanation, String code) {
        Signal<Boolean> running = taskList.isRunning(action);
        Button start = new Button(running.map(r -> r ? action.name() + " running…" : "Start"));
        start.addClickListener(e -> {
            Task task = action.startTask(taskList);
            if (action.blockingMode() == BlockingMode.BLOCKING) {
                new BlockingTaskDialog(task).open();
            }
        });
        start.setAriaLabel("Start " + action.name());
        start.addThemeVariants(ButtonVariant.PRIMARY);
        start.bindEnabled(Signal.not(running));
        Div card = new Div(new H2(action.name()));
        if (!kind.isEmpty()) {
            Span kindLabel = new Span(kind);
            kindLabel.addClassName("action-kind");
            card.add(kindLabel, new Paragraph(explanation));
        }
        card.add(start);
        if (!code.isEmpty()) {
            Pre pre = new Pre(code);
            pre.addClassName("action-code");
            Details details = new Details("Show the code", pre);
            details.addClassName("action-code-details");
            card.add(details);
        }
        card.addClassName("action-card");
        return card;
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
