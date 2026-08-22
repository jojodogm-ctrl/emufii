package eu.emufii.app.profile

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The friends list, against real SharedPreferences.
 *
 * Instrumented rather than JVM: the store's whole job is surviving a restart,
 * and a mocked preferences object would test the mock. Reloading it from a
 * second instance is the closest thing to relaunching the app.
 */
@RunWith(AndroidJUnit4::class)
class FriendStoreTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val self = FriendCode.generate()

    private fun newStore() = FriendStore(context)

    @Before
    fun clean() {
        context.getSharedPreferences("emufii_friends", android.content.Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    @Test
    fun addsAFriendAndKeepsThemAcrossRestarts() {
        val store = newStore()
        val code = FriendCode.generate()

        val result = store.add(FriendCode.format(code), self)
        assertTrue(result is AddFriendResult.Added)
        assertEquals(listOf(code), store.friends.value.map { it.code })

        // A second instance reads what the first wrote, the restart case.
        assertEquals(listOf(code), newStore().friends.value.map { it.code })
    }

    @Test
    fun refusesInvalidDuplicateAndSelf() {
        val store = newStore()
        val code = FriendCode.generate()
        store.add(code, self)

        assertEquals(AddFriendResult.Invalid, store.add("PAS-UN-CODE", self))
        assertEquals(AddFriendResult.AlreadyAdded, store.add(code, self))
        assertEquals(AddFriendResult.AlreadyAdded, store.add(FriendCode.format(code), self))
        assertEquals(AddFriendResult.Self, store.add(self, self))
        assertEquals(1, store.friends.value.size)
    }

    @Test
    fun namesAreLearnedAndKept() {
        val store = newStore()
        val code = FriendCode.generate()
        store.add(code, self)
        assertNull(store.friends.value.single().name)

        store.noteNames(mapOf(code to "Léa"))
        assertEquals("Léa", store.friends.value.single().name)
        assertEquals("Léa", newStore().friends.value.single().name)

        // A name we were told nothing about this round stays as it was.
        store.noteNames(emptyMap())
        assertEquals("Léa", store.friends.value.single().name)
    }

    @Test
    fun removesAndClears() {
        val store = newStore()
        val a = FriendCode.generate()
        val b = FriendCode.generate()
        store.add(a, self)
        store.add(b, self)

        store.remove(a)
        assertEquals(listOf(b), store.friends.value.map { it.code })
        assertEquals(listOf(b), newStore().friends.value.map { it.code })

        store.clear()
        assertTrue(store.friends.value.isEmpty())
        assertTrue(newStore().friends.value.isEmpty())
    }

    /**
     * Anything unreadable is dropped rather than taking the list down with it:
     * a store that throws on load is an app that won't open.
     */
    @Test
    fun survivesCorruptedStorage() {
        context.getSharedPreferences("emufii_friends", android.content.Context.MODE_PRIVATE)
            .edit().putString("list", "{not json at all").commit()
        assertTrue(newStore().friends.value.isEmpty())

        context.getSharedPreferences("emufii_friends", android.content.Context.MODE_PRIVATE)
            .edit().putString("list", """[{"code":"NOPE"},{"nothing":1}]""").commit()
        assertTrue(newStore().friends.value.isEmpty())
    }
}
