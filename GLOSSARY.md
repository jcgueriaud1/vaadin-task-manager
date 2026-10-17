# Vaadin Task Manager

A demo of running long operations from a Vaadin UI. Users start them, follow their progress and get notified when they finish.

## Language

### Actions and Tasks

**Action**:
Something the user can start, defined by a name and whether it is blocking. It holds no work: the application runs that itself, on a thread of its choosing. An Action has at most one Running Task per session.
_Avoid_: Job, operation, task (for the definition)

**Task**:
One run of an Action, with its own state, progress, status text and outcome.
_Avoid_: Job, execution, run

**Blocking Task**:
A Task that prevents the user from working in the view that started it, whether by a start or a Retry, until it finishes or is cancelled.
_Avoid_: Modal task, synchronous task

**Non-blocking Task**:
A Task that runs while the user keeps working. The user follows it in the task center and the tray.
_Avoid_: Background task, async task

### Task state

**Task state**:
Where a Task is in its life. It is Running, Succeeded, Failed or Cancelled. A Task starts as Running and ends in exactly one of the other three.
_Avoid_: Status (reserved for status text)

**Finished Task**:
A Task that is Succeeded, Failed or Cancelled.
_Avoid_: Done task, completed task (Succeeded is the success case only)

**Outcome**:
How a Finished Task ended: Succeeded, with its Result summary if it has one; Failed, with the error that stopped it; or Cancelled.
_Avoid_: Result, end state

**Cancellation**:
The user asks a Running Task to stop. The Task becomes Cancelled when its Action stops.
_Avoid_: Abort, kill

**Progress**:
How much of a Task is done, as a fraction from 0 to 1. A Task has it once its work first reports it; until then it shows as indeterminate.
_Avoid_: Percentage, completion

**Status text**:
A short line that the Action updates as it goes, saying what the Task is doing now. For example "Step 2: compressing".
_Avoid_: Message, step, label

**Result summary**:
An optional short text a Succeeded Task ends with, describing what it produced. For example "5 PDF files".
_Avoid_: Result, output

**Retry**:
Starting a new Task of the same Action, with the same work, from a Failed or Cancelled Task. A Task can be started as never retryable, for work that must not run twice; it can't be retried. The old Task stays as it was. Retrying a Blocking Task blocks the view it was retried from.
_Avoid_: Rerun, restart

### Where Tasks live

**Task list**:
All Tasks of one user session, Running and Finished, seen identically in every browser tab of that session.
_Avoid_: Queue, task registry

**Task history**:
The Finished Tasks in the Task list. They are kept until the user clears them or the session ends.
_Avoid_: Log, archive

**Unseen**:
A Finished Task the user hasn't looked at yet, because no tab of the session was visible when it finished. It stops being Unseen as soon as any tab of the session is visible.
_Avoid_: Unread, new, missed

**Dismissed**:
A Finished Task the user has removed from the tray. It is still in the Task history.
_Avoid_: Cleared, hidden, closed
