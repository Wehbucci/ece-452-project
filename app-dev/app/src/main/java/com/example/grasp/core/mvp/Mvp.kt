package com.example.grasp.core.mvp

/**
 * Base contracts for Grasp's **MVP (Model–View–Presenter)** architecture.

 * Each screen defines a `XxxContract` that nests its `View` and `Presenter` interfaces, an
 * `XxxPresenter` that implements the logic, and an `XxxScreen` Composable that realizes the
 * View. See [com.example.grasp.ui.feature.home] for the canonical example to copy.
 *
 */

/** Marker for anything a Presenter is allowed to drive. A screen's `View` interface extends this. */
interface MvpView

/**
 * Base Presenter. Holds a reference to its [View] between [attach] and [detach].
 *
 * Lifecycle in Compose: the screen Composable calls [attach] inside a `DisposableEffect`
 * (passing an object that implements the screen's `View`) and [detach] in `onDispose`.
 * After [detach] a Presenter must not touch the View (it may be gone).
 *
 * @param V the screen-specific View interface (which itself extends [MvpView]).
 */
interface MvpPresenter<V : MvpView> {
    /** Called when the screen becomes active. Implementations usually load initial data here. */
    fun attach(view: V)

    /** Called when the screen leaves composition. Cancel work and drop the View reference. */
    fun detach()
}

/**
 * Convenience base class so concrete presenters don't re-implement attach/detach plumbing.
 *
 * Subclasses access the current view via [view] (null after detach — always null-check or
 * use [view] only inside event handlers that can't run while detached). Override
 * [onViewAttached] to kick off initial loading instead of overriding [attach] directly.
 */
abstract class BasePresenter<V : MvpView> : MvpPresenter<V> {

    /** The currently attached View, or null when the screen is not on-screen. */
    protected var view: V? = null
        private set

    final override fun attach(view: V) {
        this.view = view
        onViewAttached()
    }

    override fun detach() {
        view = null
    }

    /** Hook for subclasses: the View is now attached — safe to load data and render. */
    protected open fun onViewAttached() {}
}
