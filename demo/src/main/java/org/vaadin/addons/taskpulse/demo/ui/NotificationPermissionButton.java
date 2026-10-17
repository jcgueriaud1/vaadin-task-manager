package org.vaadin.addons.taskpulse.demo.ui;

import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.page.PageVisibility;
import com.vaadin.flow.signals.Signal;

/**
 * Asks the browser for permission to show native notifications, only when
 * clicked, and shows the current permission: not asked, granted or denied.
 */
public class NotificationPermissionButton extends Button {

    private boolean effectAdded;

    public NotificationPermissionButton() {
        setAriaLabel("Notification permission");
        addClassName("notification-permission");
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
