package com.example.taskmanager.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import com.example.taskmanager.task.BlockingMode;
import com.vaadin.browserless.SpringBrowserlessTest;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Pre;

/** The real Actions page, with the demo Actions. Nothing is started, so their work never runs. */
@SpringBootTest
@Import(TestTaskConfig.class)
class DemoActionsViewTest extends SpringBrowserlessTest {

    @BeforeEach
    void open() {
        navigate(ActionsView.class);
    }

    @Test
    void eachDemoActionHasACardWithItsCode() {
        List<Div> cards = $(Div.class).withClassName("action-card").all();
        assertEquals(List.of("Send newsletter", "Generate report", "Sync with server", "Import data"),
                cards.stream().map(card -> $(H2.class).from(card).single().getText()).toList());
        for (Div card : cards) {
            String name = $(H2.class).from(card).single().getText();
            assertTrue($(Pre.class).from(card).single().getText().contains('"' + name + '"'), name);
        }
    }

    @Test
    void theDemoActionsCoverEachBlockingMode() {
        assertEquals(BlockingMode.NON_BLOCKING, ActionsView.SEND_NEWSLETTER.blockingMode());
        assertEquals(BlockingMode.NON_BLOCKING, ActionsView.GENERATE_REPORT.blockingMode());
        assertEquals(BlockingMode.BLOCKING, ActionsView.IMPORT_DATA.blockingMode());
    }
}
