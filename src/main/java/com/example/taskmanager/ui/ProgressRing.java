package com.example.taskmanager.ui;

import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.signals.Signal;

/**
 * A small circular progress indicator, drawn in CSS from the {@code --value}
 * custom property. Indeterminate, it spins a fixed arc instead.
 */
public class ProgressRing extends Span {

    private double value;

    /** Follows the given Progress, from 0 to 1. */
    public ProgressRing(Signal<Double> progress) {
        addClassName("progress-ring");
        getElement().setAttribute("role", "progressbar");
        getElement().setAttribute("aria-valuemin", "0");
        getElement().setAttribute("aria-valuemax", "100");
        Signal.effect(this, () -> setValue(progress.get()));
    }

    /** Spins without a value, for Tasks whose Action doesn't report Progress. */
    public ProgressRing() {
        addClassNames("progress-ring", "indeterminate");
        getElement().setAttribute("role", "progressbar");
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
