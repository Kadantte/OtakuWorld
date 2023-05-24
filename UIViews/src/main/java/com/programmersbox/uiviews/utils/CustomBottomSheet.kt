package com.programmersbox.uiviews.utils

import android.annotation.SuppressLint
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.MaterialTheme
import androidx.compose.material.ModalBottomSheetDefaults
import androidx.compose.material.ModalBottomSheetState
import androidx.compose.material.ModalBottomSheetValue
import androidx.compose.material.SwipeProgress
import androidx.compose.material.SwipeableDefaults
import androidx.compose.material.contentColorFor
import androidx.compose.material.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.SaveableStateHolder
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.navigation.FloatingWindow
import androidx.navigation.NamedNavArgument
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDeepLink
import androidx.navigation.NavDestination
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.Navigator
import androidx.navigation.NavigatorState
import androidx.navigation.compose.LocalOwnersProvider
import androidx.navigation.get
import com.programmersbox.uiviews.utils.BottomSheetNavigatorCustom.Destination
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.transform
import java.util.concurrent.CancellationException

/**
 * The state of a [ModalBottomSheetLayout] that the [BottomSheetNavigatorCustom] drives
 *
 * @param sheetState The sheet state that is driven by the [BottomSheetNavigatorCustom]
 */
@OptIn(ExperimentalMaterialApi::class)
@Stable
class BottomSheetNavigatorSheetState(internal val sheetState: ModalBottomSheetState) {
    /**
     * @see ModalBottomSheetState.isVisible
     */
    val isVisible: Boolean
        get() = sheetState.isVisible

    /**
     * @see ModalBottomSheetState.currentValue
     */
    val currentValue: ModalBottomSheetValue
        get() = sheetState.currentValue

    /**
     * @see ModalBottomSheetState.targetValue
     */
    val targetValue: ModalBottomSheetValue
        get() = sheetState.targetValue

    /**
     * @see ModalBottomSheetState.offset
     */
    @Deprecated(
        message = "BottomSheetNavigatorSheetState#offset has been removed",
        level = DeprecationLevel.ERROR
    )
    val offset: State<Float>
        get() = error("BottomSheetNavigatorSheetState#offset has been removed")

    /**
     * @see ModalBottomSheetState.direction
     */
    @Deprecated(
        message = "BottomSheetNavigatorSheetState#direction has been removed",
        level = DeprecationLevel.ERROR
    )
    val direction: Float
        get() = error("BottomSheetNavigatorSheetState#direction has been removed.")

    /**
     * @see ModalBottomSheetState.progress
     */
    @Deprecated(
        message = "BottomSheetNavigatorSheetState#progress has been removed",
        level = DeprecationLevel.ERROR
    )
    val progress: SwipeProgress<ModalBottomSheetValue>
        get() = error("BottomSheetNavigatorSheetState#progress has been removed")
}

/**
 * Create and remember a [BottomSheetNavigatorCustom]
 */
@OptIn(ExperimentalMaterialApi::class)
@Composable
fun rememberBottomSheetNavigator(
    animationSpec: AnimationSpec<Float> = SwipeableDefaults.AnimationSpec
): BottomSheetNavigatorCustom {
    val sheetState = rememberModalBottomSheetState(
        ModalBottomSheetValue.Hidden,
        animationSpec = animationSpec
    )
    return remember { BottomSheetNavigatorCustom(sheetState) }
}

/**
 * Navigator that drives a [ModalBottomSheetState] for use of [ModalBottomSheetLayout]s
 * with the navigation library. Every destination using this Navigator must set a valid
 * [Composable] by setting it directly on an instantiated [Destination] or calling
 * [androidx.navigation.compose.material.bottomSheet].
 *
 * <b>The [sheetContent] [Composable] will always host the latest entry of the back stack. When
 * navigating from a [BottomSheetNavigatorCustom.Destination] to another
 * [BottomSheetNavigatorCustom.Destination], the content of the sheet will be replaced instead of a
 * new bottom sheet being shown.</b>
 *
 * When the sheet is dismissed by the user, the [state]'s [NavigatorState.backStack] will be popped.
 *
 * The primary constructor is not intended for public use. Please refer to
 * [rememberBottomSheetNavigator] instead.
 *
 * @param sheetState The [ModalBottomSheetState] that the [BottomSheetNavigatorCustom] will use to
 * drive the sheet state
 */
@OptIn(ExperimentalMaterialApi::class)
@Navigator.Name("BottomSheetNavigator")
class BottomSheetNavigatorCustom(
    internal val sheetState: ModalBottomSheetState
) : Navigator<BottomSheetNavigatorCustom.Destination>() {

    private var attached by mutableStateOf(false)

    /**
     * Get the back stack from the [state]. In some cases, the [sheetContent] might be composed
     * before the Navigator is attached, so we specifically return an empty flow if we aren't
     * attached yet.
     */
    private val backStack: StateFlow<List<NavBackStackEntry>>
        get() = if (attached) {
            state.backStack
        } else {
            MutableStateFlow(emptyList())
        }

    /**
     * Get the transitionsInProgress from the [state]. In some cases, the [sheetContent] might be
     * composed before the Navigator is attached, so we specifically return an empty flow if we
     * aren't attached yet.
     */
    internal val transitionsInProgress: StateFlow<Set<NavBackStackEntry>>
        get() = if (attached) {
            state.transitionsInProgress
        } else {
            MutableStateFlow(emptySet())
        }

    /**
     * Access properties of the [ModalBottomSheetLayout]'s [ModalBottomSheetState]
     */
    val navigatorSheetState = BottomSheetNavigatorSheetState(sheetState)

    /**
     * A [Composable] function that hosts the current sheet content. This should be set as
     * sheetContent of your [ModalBottomSheetLayout].
     */
    val sheetContent: @Composable ColumnScope.() -> Unit = {
        val saveableStateHolder = rememberSaveableStateHolder()
        val transitionsInProgressEntries by transitionsInProgress.collectAsState()

        // The latest back stack entry, retained until the sheet is completely hidden
        // While the back stack is updated immediately, we might still be hiding the sheet, so
        // we keep the entry around until the sheet is hidden
        val retainedEntry by produceState<NavBackStackEntry?>(
            initialValue = null,
            key1 = backStack
        ) {
            backStack
                .transform { backStackEntries ->
                    // Always hide the sheet when the back stack is updated
                    // Regardless of whether we're popping or pushing, we always want to hide
                    // the sheet first before deciding whether to re-show it or keep it hidden
                    try {
                        sheetState.hide()
                    } catch (_: CancellationException) {
                        // We catch but ignore possible cancellation exceptions as we don't want
                        // them to bubble up and cancel the whole produceState coroutine
                    } finally {
                        emit(backStackEntries.lastOrNull())
                    }
                }
                .collect {
                    value = it
                }
        }

        if (retainedEntry != null) {
            LaunchedEffect(retainedEntry) {
                sheetState.show()
            }
        }

        SheetContentHost(
            backStackEntry = retainedEntry,
            sheetState = sheetState,
            saveableStateHolder = saveableStateHolder,
            onSheetShown = {
                transitionsInProgressEntries.forEach(state::markTransitionComplete)
            },
            onSheetDismissed = { backStackEntry ->
                // Sheet dismissal can be started through popBackStack in which case we have a
                // transition that we'll want to complete
                if (transitionsInProgressEntries.contains(backStackEntry)) {
                    state.markTransitionComplete(backStackEntry)
                }
                // If there is no transition in progress, the sheet has been dimissed by the
                // user (for example by tapping on the scrim or through an accessibility action)
                // In this case, we will immediately pop without a transition as the sheet has
                // already been hidden
                else {
                    state.pop(popUpTo = backStackEntry, saveState = false)
                }
            }
        )
    }

    override fun onAttach(state: NavigatorState) {
        super.onAttach(state)
        attached = true
    }

    override fun createDestination(): Destination = Destination(
        navigator = this,
        content = {}
    )

    @SuppressLint("NewApi") // b/187418647
    override fun navigate(
        entries: List<NavBackStackEntry>,
        navOptions: NavOptions?,
        navigatorExtras: Extras?
    ) {
        entries.forEach { entry ->
            state.pushWithTransition(entry)
        }
    }

    override fun popBackStack(popUpTo: NavBackStackEntry, savedState: Boolean) {
        state.popWithTransition(popUpTo, savedState)
    }

    /**
     * [NavDestination] specific to [BottomSheetNavigatorCustom]
     */
    @NavDestination.ClassType(Composable::class)
    class Destination(
        navigator: BottomSheetNavigatorCustom,
        internal val content: @Composable ColumnScope.(NavBackStackEntry) -> Unit
    ) : NavDestination(navigator), FloatingWindow
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
internal fun ColumnScope.SheetContentHost(
    backStackEntry: NavBackStackEntry?,
    sheetState: ModalBottomSheetState,
    saveableStateHolder: SaveableStateHolder,
    onSheetShown: (entry: NavBackStackEntry) -> Unit,
    onSheetDismissed: (entry: NavBackStackEntry) -> Unit,
) {
    if (backStackEntry != null) {
        val currentOnSheetShown by rememberUpdatedState(onSheetShown)
        val currentOnSheetDismissed by rememberUpdatedState(onSheetDismissed)
        LaunchedEffect(sheetState, backStackEntry) {
            snapshotFlow { sheetState.isVisible }
                // We are only interested in changes in the sheet's visibility
                .distinctUntilChanged()
                // distinctUntilChanged emits the initial value which we don't need
                .drop(1)
                .collect { visible ->
                    if (visible) {
                        currentOnSheetShown(backStackEntry)
                    } else {
                        currentOnSheetDismissed(backStackEntry)
                    }
                }
        }
        backStackEntry.LocalOwnersProvider(saveableStateHolder) {
            val content =
                (backStackEntry.destination as BottomSheetNavigatorCustom.Destination).content
            content(backStackEntry)
        }
    }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun ModalBottomSheetLayout(
    bottomSheetNavigator: BottomSheetNavigatorCustom,
    modifier: Modifier = Modifier,
    sheetShape: Shape = MaterialTheme.shapes.large,
    sheetElevation: Dp = ModalBottomSheetDefaults.Elevation,
    sheetBackgroundColor: Color = MaterialTheme.colors.surface,
    sheetContentColor: Color = contentColorFor(sheetBackgroundColor),
    scrimColor: Color = ModalBottomSheetDefaults.scrimColor,
    content: @Composable () -> Unit
) {
    androidx.compose.material.ModalBottomSheetLayout(
        sheetState = bottomSheetNavigator.sheetState,
        sheetContent = bottomSheetNavigator.sheetContent,
        modifier = modifier,
        sheetShape = sheetShape,
        sheetElevation = sheetElevation,
        sheetBackgroundColor = sheetBackgroundColor,
        sheetContentColor = sheetContentColor,
        scrimColor = scrimColor,
        content = content
    )
}

@SuppressLint("NewApi") // b/187418647
public fun NavGraphBuilder.bottomSheet(
    route: String,
    arguments: List<NamedNavArgument> = emptyList(),
    deepLinks: List<NavDeepLink> = emptyList(),
    content: @Composable ColumnScope.(backstackEntry: NavBackStackEntry) -> Unit
) {
    addDestination(
        BottomSheetNavigatorCustom.Destination(
            provider[BottomSheetNavigatorCustom::class],
            content
        ).apply {
            this.route = route
            arguments.forEach { (argumentName, argument) ->
                addArgument(argumentName, argument)
            }
            deepLinks.forEach { deepLink ->
                addDeepLink(deepLink)
            }
        }
    )
}