package com.example.taskmanager;

import java.util.List;

import com.example.taskmanager.task.AsyncAction;

/** The Actions the demo offers on the Actions page. Tests replace this bean. */
public record DemoActions(List<AsyncAction<?>> actions) {
}
