package co.edu.uniandes.vinilos.ui.common

/**
 * True only when a submit turns a form without errors into one with errors (WCAG 3.3.1): then the form moves the
 * input focus to the first field with an error, and TalkBack follows it. A rebuilt view (no previous render) and a
 * form that already showed errors keep the focus where it is, so typing a fix never moves it.
 */
internal fun errorsJustAppeared(
    hadErrors: Boolean?,
    hasErrors: Boolean,
): Boolean = hadErrors == false && hasErrors
