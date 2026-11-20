package repro

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.NonSkippableComposable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.neverEqualPolicy
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

// The pattern from "3.A Optimize whole-collection updates with neverEqualPolicy()", verbatim apart from Item = String.
class ActiveQueueManager {
    private val list = mutableListOf<String>()
    val items = mutableStateOf(list, neverEqualPolicy())

    fun enqueue(item: String) {
        list.add(item)
        items.value = list // Load-bearing assignment triggers recomposition
    }
}

// Same manager, but each update publishes a new list.
class NewListQueueManager {
    val items = mutableStateOf(listOf<String>())

    fun enqueue(item: String) {
        items.value = items.value + item
    }
}

// Same manager, backed by mutableStateListOf().
class SnapshotListQueueManager {
    val items = mutableStateListOf<String>()

    fun enqueue(item: String) {
        items.add(item)
    }
}

// A restartable child that takes the list as an ordinary List parameter.
@Composable
fun QueueList(items: List<String>) {
    Column {
        items.forEach { BasicText("child: $it") }
    }
}

// The same child with skipping turned off, to show that skipping is what hides the update.
@Composable
@NonSkippableComposable
fun NonSkippableQueueList(items: List<String>) {
    Column {
        items.forEach { BasicText("child: $it") }
    }
}

@RunWith(AndroidJUnit4::class)
class NeverEqualPolicyStaleChildTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun skillPattern_childShowsNewItem() {
        val manager = ActiveQueueManager()
        manager.enqueue("a")
        compose.setContent {
            val items = manager.items.value
            Column {
                BasicText("parent: ${items.size} items")
                QueueList(items)
            }
        }

        compose.runOnIdle { manager.enqueue("b") }

        compose.onNodeWithText("parent: 2 items").assertExists() // the parent recomposed
        compose.onNodeWithText("child: b").assertExists() // fails: QueueList skipped (same List instance)
    }

    @Test
    fun newListEachUpdate_childShowsNewItem() {
        val manager = NewListQueueManager()
        manager.enqueue("a")
        compose.setContent {
            val items = manager.items.value
            Column {
                BasicText("parent: ${items.size} items")
                QueueList(items)
            }
        }

        compose.runOnIdle { manager.enqueue("b") }

        compose.onNodeWithText("parent: 2 items").assertExists()
        compose.onNodeWithText("child: b").assertExists()
    }

    @Test
    fun snapshotStateList_childShowsNewItem() {
        val manager = SnapshotListQueueManager()
        manager.enqueue("a")
        compose.setContent {
            val items = manager.items
            Column {
                BasicText("parent: ${items.size} items")
                QueueList(items)
            }
        }

        compose.runOnIdle { manager.enqueue("b") }

        compose.onNodeWithText("parent: 2 items").assertExists()
        compose.onNodeWithText("child: b").assertExists()
    }

    @Test
    fun skillPattern_nonSkippableChild_showsNewItem() {
        val manager = ActiveQueueManager()
        manager.enqueue("a")
        compose.setContent {
            val items = manager.items.value
            Column {
                BasicText("parent: ${items.size} items")
                NonSkippableQueueList(items)
            }
        }

        compose.runOnIdle { manager.enqueue("b") }

        compose.onNodeWithText("parent: 2 items").assertExists()
        compose.onNodeWithText("child: b").assertExists()
    }
}
