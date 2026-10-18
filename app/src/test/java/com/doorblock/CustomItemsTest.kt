package com.doorblock

import com.doorblock.core.AddResult
import com.doorblock.core.CustomItems
import com.doorblock.core.FilterEngine
import com.doorblock.core.ItemKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomItemsTest {
    private val legacy = setOf("example.org", "Sub.Example.com", "bad phrase", "cat", "فیلم سکسی", "a.b c", " ", "")

    @Test fun kindDetectionMatchesTheRuleUsedByTheFilter() {
        assertEquals(ItemKind.SITE, CustomItems.kindOf("example.org"))
        assertEquals(ItemKind.SITE, CustomItems.kindOf("  https://x.com/p  "))
        assertEquals(ItemKind.WORD, CustomItems.kindOf("bad phrase"))
        assertEquals(ItemKind.WORD, CustomItems.kindOf("cat"))
        assertEquals(ItemKind.WORD, CustomItems.kindOf("a.b c"))   // نقطه دارد ولی فاصله هم دارد
        assertNull(CustomItems.kindOf(" "))
        assertNull(CustomItems.kindOf(""))
    }

    @Test fun legacyDataIsSplitWithoutLosingAnything() {
        val sites = CustomItems.itemsOf(legacy, ItemKind.SITE)
        val words = CustomItems.itemsOf(legacy, ItemKind.WORD)
        assertEquals(listOf("example.org", "Sub.Example.com"), sites)
        assertEquals(setOf("bad phrase", "cat", "فیلم سکسی", "a.b c"), words.toSet())
        // هر مورد معتبر دقیقا در یکی از دو فهرست است
        val valid = legacy.filter { CustomItems.kindOf(it) != null }.toSet()
        assertEquals(valid, (sites + words).toSet())
        assertEquals(sites.size, CustomItems.count(legacy, ItemKind.SITE))
        assertEquals(words.size, CustomItems.count(legacy, ItemKind.WORD))
    }

    @Test fun orderIsStableAndCaseInsensitive() {
        val s = setOf("b.com", "A.com", "c.com", "B.com")
        val first = CustomItems.itemsOf(s, ItemKind.SITE)
        assertEquals(first, CustomItems.itemsOf(s.reversed().toSet(), ItemKind.SITE))
        assertEquals(listOf("A.com", "B.com", "b.com", "c.com"), first)
    }

    @Test fun addStoresTrimmedValueAndKeepsExistingItems() {
        val r = CustomItems.add(setOf("old.com"), "  new.com  ") as AddResult.Added
        assertEquals(setOf("old.com", "new.com"), r.items)
    }

    @Test fun addRejectsEmptyDuplicateAndTooLong() {
        assertTrue(CustomItems.add(setOf("a.com"), "   ") is AddResult.Empty)
        assertTrue(CustomItems.add(setOf("a.com"), "A.COM ") is AddResult.Duplicate)
        assertTrue(CustomItems.add(setOf("bad phrase"), "Bad Phrase") is AddResult.Duplicate)
        assertTrue(CustomItems.add(emptySet(), "x".repeat(CustomItems.MAX_LEN + 1)) is AddResult.TooLong)
        assertTrue(CustomItems.add(emptySet(), "x".repeat(CustomItems.MAX_LEN)) is AddResult.Added)
    }

    @Test fun removeDeletesExactlyOneItem() {
        val before = setOf("a.com", "A.com", "b.com", "word", "two words")
        val after = CustomItems.remove(before, "A.com")
        assertEquals(setOf("a.com", "b.com", "word", "two words"), after)
        assertEquals(before.size - 1, after.size)
        // حذف چیزی که وجود ندارد هیچ‌چیز را عوض نمی‌کند
        assertEquals(before, CustomItems.remove(before, "missing.com"))
    }

    @Test fun removingEveryItemOneByOneNeverTouchesTheOthers() {
        var current: Set<String> = legacy
        for (item in CustomItems.itemsOf(legacy, ItemKind.SITE) + CustomItems.itemsOf(legacy, ItemKind.WORD)) {
            val expected = current - item
            current = CustomItems.remove(current, item)
            assertEquals(expected, current)
        }
        // فقط موارد بی‌اثر (خالی) باقی می‌مانند و دور ریخته نشده‌اند
        assertEquals(setOf(" ", ""), current)
    }

    @Test fun emptyListsAreEmpty() {
        assertTrue(CustomItems.itemsOf(emptySet(), ItemKind.SITE).isEmpty())
        assertEquals(0, CustomItems.count(emptySet(), ItemKind.WORD))
    }

    @Test fun addThenFilterDetectsTheNewItems_andRemoveStopsIt() {
        var items: Set<String> = emptySet()
        items = (CustomItems.add(items, "mine.example.org") as AddResult.Added).items
        items = (CustomItems.add(items, "my secret phrase") as AddResult.Added).items
        var engine = FilterEngine.build(items)
        assertTrue(engine.isBlocked("https://sub.mine.example.org"))
        assertTrue(engine.isBlocked("my secret phrase"))
        items = CustomItems.remove(items, "mine.example.org")
        engine = FilterEngine.build(items)
        assertFalse(engine.isBlocked("https://sub.mine.example.org"))
        assertTrue(engine.isBlocked("my secret phrase"))   // مورد دیگر دست‌نخورده
    }
}
