package com.pedaree.shared

import com.pedaree.shared.domain.Satuan
import com.pedaree.shared.tampil.formatJumlah
import kotlin.test.Test
import kotlin.test.assertEquals

class FormatTest {
    @Test fun beratDiBawahSeribuSatuDesimal() {
        assertEquals("250 g", formatJumlah(250.0, Satuan.GRAM))
        assertEquals("250,5 g", formatJumlah(250.5, Satuan.GRAM))
        assertEquals("0,1 ml", formatJumlah(0.05, Satuan.ML))
    }

    @Test fun seribuKeAtasTanpaDesimalDenganTitikRibuan() {
        assertEquals("1.500 g", formatJumlah(1500.0, Satuan.GRAM))
        assertEquals("50.000 ml", formatJumlah(50000.0, Satuan.ML))
        assertEquals("1.000 g", formatJumlah(999.96, Satuan.GRAM)) // membulat ke 1000,0 -> tanpa desimal
    }

    @Test fun satuanHitungSelaluBulat() {
        assertEquals("2 ikat", formatJumlah(2.0, Satuan.IKAT))
        assertEquals("6 pcs", formatJumlah(6.0, Satuan.PCS))
        assertEquals("1 btg", formatJumlah(0.6, Satuan.BATANG))
    }
}
