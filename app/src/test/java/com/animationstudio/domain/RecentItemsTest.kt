package com.animationstudio.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class RecentItemsTest {
    @Test fun maisRecentePrimeiro() {
        assertEquals(listOf("c", "b", "a"), RecentItems.push(RecentItems.push(listOf("a"), "b"), "c"))
    }

    @Test fun repetidoSobeParaOTopoSemDuplicar() {
        assertEquals(listOf("a", "c", "b"), RecentItems.push(listOf("c", "b", "a"), "a"))
    }

    @Test fun respeitaOLimite() {
        var l = emptyList<String>()
        for (i in 1..30) l = RecentItems.push(l, "p$i")
        assertEquals(RecentItems.DEFAULT_MAX, l.size)
        assertEquals("p30", l.first())
        assertEquals("p11", l.last())
    }

    @Test fun codificaEDescodifica() {
        val l = listOf("ink_g", "my_abc", "pencil")
        assertEquals(l, RecentItems.decode(RecentItems.encode(l)))
    }

    @Test fun descodificaLixo() {
        assertEquals(emptyList<String>(), RecentItems.decode(null))
        assertEquals(emptyList<String>(), RecentItems.decode(""))
        assertEquals(listOf("a", "b"), RecentItems.decode(" a, ,b,a,"))
    }
}
