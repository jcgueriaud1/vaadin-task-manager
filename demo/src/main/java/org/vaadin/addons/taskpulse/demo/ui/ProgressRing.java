package org.vaadin.addons.taskpulse.demo.ui;

import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.signals.Signal;

/**
 * A small circular progress indicator, drawn in CSS from the {@code --value}
 * custom property. Indeterminate, it spins a fixed arc instead.
 */
public class ProgressRing extends Span {

    private double value;

    /**
     * Follows the given Progress, from 0 to 1, while it is determinate, and spins
     * otherwise, for example until a Task's work first reports Progress.
     */
    public ProgressRing(Signal<Double> progress, Signal<Boolean> determinate) {
        addClassName("progress-ring");
        bindClassName("indeterminate", Signal.not(determinate));
        getElement().setAttribute("role", "progressbar");
        getElement().setAttribute("aria-valuemin", "0");
        getElement().setAttribute("aria-valuemax", "100");
        Signal.effect(this, () -> {
            if (determinate.get()) {
                setValue(progress.get());
            } else {
                getElement().removeAttribute("aria-valuenow");
            }
        });
    }

    public boolean isIndeterminate() {
        return hasClassName("indeterminate");
    }

    public double getValue() {
        return value;
    }

    private void setValue(double value) {
        this.value = value;
        getStyle().set("--value", String.valueOf(value));
        getElement().setAttribute("aria-valuenow", String.valueOf(Math.round(value * 100)));
    }
}
