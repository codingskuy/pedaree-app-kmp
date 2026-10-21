package com.pedaree.shared

import com.pedaree.shared.nav.Rute
import com.pedaree.shared.nav.TumpukanRute
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NavigasiTest {
    @Test fun mulaiDiDapur() {
        assertEquals(Rute.Dapur, TumpukanRute().atas)
    }

    @Test fun bukaDetailLaluKembali() {
        val t = TumpukanRute()
        t.buka(Rute.DetailItem("itm_1"))
        assertEquals(Rute.DetailItem("itm_1"), t.atas)
        assertTrue(t.kembali())
        assertEquals(Rute.Dapur, t.atas)
    }

    @Test fun dasarTidakBisaDibuang() {
        val t = TumpukanRute()
        assertFalse(t.kembali())
        assertEquals(listOf<Rute>(Rute.Dapur), t.daftar)
    }

    @Test fun ketukGandaTidakMenumpuk() {
        val t = TumpukanRute()
        t.buka(Rute.DetailItem("itm_1"))
        t.buka(Rute.DetailItem("itm_1"))
        assertEquals(2, t.daftar.size)
    }

    @Test fun rutaAlurCatatBisaDitumpukDanKembali() {
        val t = TumpukanRute()
        t.buka(Rute.DetailItem("itm_1"))
        t.buka(Rute.CatatMasuk("itm_1"))
        t.buka(Rute.Buang("itm_1", "btc_1"))
        assertEquals(4, t.daftar.size)
        assertTrue(t.kembali())
        assertEquals(Rute.CatatMasuk("itm_1"), t.atas)
        assertTrue(t.kembali())
        assertEquals(Rute.DetailItem("itm_1"), t.atas)
    }
}
