package com.mahdi.platen.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mahdi.platen.appContainer
import com.mahdi.platen.data.Settings
import com.mahdi.platen.ui.editor.EditorRoute
import com.mahdi.platen.ui.notes.NotesRoute
import com.mahdi.platen.ui.settings.SettingsScreen
import com.mahdi.platen.ui.studio.StudioScreen
import com.mahdi.platen.ui.theme.LocalPalette
import com.mahdi.platen.ui.theme.PlatenTheme
import kotlinx.coroutines.launch

/** Routes are plain strings so the back stack survives rotation and process death. */
object Routes {
    const val LIST = "list"
    const val SETTINGS = "settings"
    const val STUDIO = "studio"
    fun editor(id: Long) = "editor/$id"
    fun editorId(route: String): Long? = route.removePrefix("editor/").takeIf { route.startsWith("editor/") }?.toLongOrNull()
}

/** One ViewModelStore per back-stack entry, so a screen's ViewModel is cleared when it's popped. */
class RouteStores : ViewModel() {
    private val stores = HashMap<String, ViewModelStore>()
    fun owner(route: String): ViewModelStoreOwner {
        val store = stores.getOrPut(route) { ViewModelStore() }
        return object : ViewModelStoreOwner { override val viewModelStore = store }
    }
    fun retainOnly(routes: Collection<String>) {
        val gone = stores.keys - routes.toSet()
        gone.forEach { stores.remove(it)?.clear() }
    }
    override fun onCleared() { stores.values.forEach { it.clear() }; stores.clear() }
}

private val StackSaver = listSaver<List<String>, String>(save = { it }, restore = { it })

@Composable
fun PlatenRoot() {
    val context = LocalContext.current
    val c = remember { context.appContainer() }
    val loaded by c.settings.settings.collectAsStateWithLifecycle(initialValue = null)
    val settings = loaded ?: Settings()
    val scope = rememberCoroutineScope()
    var stack by rememberSaveable(stateSaver = StackSaver) { mutableStateOf(listOf(Routes.LIST)) }
    fun push(r: String) { stack = stack + r }
    fun pop() { if (stack.size > 1) stack = stack.dropLast(1) }
    val routeStores: RouteStores = viewModel()
    LaunchedEffect(stack) { routeStores.retainOnly(stack) }
    val update: ((Settings) -> Settings) -> Unit = { t -> scope.launch { c.settings.update(t) } }

    PlatenTheme(settings.theme) {
        Box(Modifier.fillMaxSize().background(LocalPalette.current.background)) {
            if (loaded == null) return@Box
            BackHandler(enabled = stack.size > 1) { pop() }
            val route = stack.last()
            val editorId = Routes.editorId(route)
            val owner = remember(route) { routeStores.owner(route) }
            CompositionLocalProvider(LocalViewModelStoreOwner provides owner) {
            when {
                editorId != null -> EditorRoute(
                    noteId = editorId,
                    onBack = ::pop,
                    onOpenSettings = { push(Routes.SETTINGS) },
                    onOpenStudio = { push(Routes.STUDIO) },
                    onNewSheet = { id -> stack = stack.dropLast(1) + Routes.editor(id) },
                )
                route == Routes.SETTINGS -> SettingsScreen(settings, update, onBack = ::pop, onOpenStudio = { push(Routes.STUDIO) })
                route == Routes.STUDIO -> StudioScreen(settings.studio, onToggle = { v -> update { it.copy(studio = v) } }, onBack = ::pop)
                else -> NotesRoute(
                    onOpen = { id -> push(Routes.editor(id)) },
                    onOpenSettings = { push(Routes.SETTINGS) },
                    onOpenStudio = { push(Routes.STUDIO) },
                )
            }
            }
        }
    }
}
