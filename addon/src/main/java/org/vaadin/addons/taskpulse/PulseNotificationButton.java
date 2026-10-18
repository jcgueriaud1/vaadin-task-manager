package org.vaadin.addons.taskpulse;

import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dependency.StyleSheet;
import com.vaadin.flow.component.page.PageVisibility;
import com.vaadin.flow.signals.Signal;

/**
 * Asks the browser for permission to show native notifications, only when
 * clicked, and shows the current permission: not asked, granted or denied.
 * {@link PulseTab} shows a native notification for each Task that finishes
 * while no tab of the session is visible, once the user granted it. The task
 * center has one in its popover; use this one in apps without a task center.
 */
@StyleSheet(PulseTaskParts.STYLESHEET)
public class PulseNotificationButton extends Button {

    private boolean effectAdded;

    /** When it attaches, it sets up the {@link PulseTab} of its browser tab if there is none yet. */
    public PulseNotificationButton() {
        setAriaLabel("Notification permission");
        addClassName("pulse-notification-button");
        setVisible(false);
        addClickListener(event -> getUI().ifPresent(ui -> ui.getPage()
                .executeJs("return Notification.requestPermission()")
                .then(String.class, this::show)));
    }

    @Override
    protected void onAttach(AttachEvent attachEvent) {
        super.onAttach(attachEvent);
        if (!effectAdded) {
            effectAdded = true;
            UI ui = attachEvent.getUI();
            PulseTab.of(ui);
            // Asks again whenever the tab is shown: another tab may have changed it
            Signal.effect(this, () -> {
                if (ui.getPage().pageVisibilitySignal().get() != PageVisibility.HIDDEN) {
                    ui.getPage()
                            .executeJs("return 'Notification' in window ? Notification.permission : 'unsupported'")
                            .then(String.class, this::show);
                }
            });
        }
    }

    private void show(String permission) {
        setVisible(!"unsupported".equals(permission));
        setEnabled("default".equals(permission));
        setText(switch (permission) {
            case "granted" -> "Notifications on";
            case "denied" -> "Notifications blocked";
            default -> "Enable notifications";
        });
    }
}
