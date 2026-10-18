package org.vaadin.addons.taskpulse.demo.ui;

import java.util.Arrays;
import java.util.Optional;

import jakarta.servlet.http.Cookie;

import com.vaadin.flow.server.VaadinRequest;
import com.vaadin.flow.theme.aura.Aura;
import com.vaadin.flow.theme.lumo.Lumo;

/**
 * The themes the demo can show, to see the add-on in each. A theme is one
 * stylesheet, plus the {@code theme} attribute of the {@code <html>} element for
 * Lumo's dark variant. The choice is kept in a cookie so that the app shell
 * loads the same theme on the next visit.
 */
public enum DemoTheme {
    LUMO_LIGHT("Lumo light", Lumo.STYLESHEET, ""),
    LUMO_DARK("Lumo dark", Lumo.STYLESHEET, Lumo.DARK),
    AURA("Aura", Aura.STYLESHEET, ""),
    CUSTOM("Custom (Aura)", "themes/custom.css", "");

    public static final DemoTheme DEFAULT = AURA;

    /** The id of the {@code <link>} element that loads the theme's stylesheet. */
    public static final String LINK_ID = "demo-theme";

    static final String COOKIE = "taskpulse-theme";

    private final String label;
    private final String stylesheet;
    private final String htmlTheme;

    DemoTheme(String label, String stylesheet, String htmlTheme) {
        this.label = label;
        this.stylesheet = stylesheet;
        this.htmlTheme = htmlTheme;
    }

    public String label() {
        return label;
    }

    public String stylesheet() {
        return stylesheet;
    }

    /** The value of the {@code theme} attribute of {@code <html>}; empty for none. */
    public String htmlTheme() {
        return htmlTheme;
    }

    /** The theme chosen in the request's cookie, or the default one. */
    public static DemoTheme of(VaadinRequest request) {
        Cookie[] cookies = request != null ? request.getCookies() : null;
        return Optional.ofNullable(cookies).stream()
                .flatMap(Arrays::stream)
                .filter(cookie -> COOKIE.equals(cookie.getName()))
                .map(cookie -> named(cookie.getValue()))
                .flatMap(Optional::stream)
                .findFirst()
                .orElse(DEFAULT);
    }

    private static Optional<DemoTheme> named(String name) {
        return Arrays.stream(values()).filter(theme -> theme.name().equals(name)).findFirst();
    }
}
