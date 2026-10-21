package com.pedaree.shared.data

import com.pedaree.shared.catat.GerbangTulis
import com.pedaree.shared.catat.HasilTulis
import com.pedaree.shared.kontrak.PerintahTulis

/**
 * Gerbang tulis nyata: SETIAP perintah lebih dulu disimpan di antrean lokal (jadi tidak hilang bila aplikasi
 * mati atau jaringan putus), baru dicoba dikirim. Daring: hasil server dikembalikan (dan entri dihapus).
 * Luring/gagal sementara: [HasilTulis.Diantrekan]; pengiriman ulang idempoten oleh [PengirimAntrean].
 * Ditolak server: [HasilTulis.Ditolak] dan entri dibuang (pengguna langsung melihat galatnya dan dapat mengubah).
 */
class GerbangTulisOffline(
    private val antrean: AntreanTulis,
    private val pengirim: PengirimAntrean,
    private val setelahTulis: () -> Unit = {},
) : GerbangTulis {
    override suspend fun kirim(perintah: PerintahTulis): HasilTulis {
        val entri = antrean.tambah(perintah)
        val hasil = pengirim.kurasSampai(entri.seq)
        // Penolakan langsung ditampilkan ke pengguna; tidak perlu ditahan di antrean.
        if (hasil is PengirimAntrean.HasilEntri.Ditolak) antrean.hapus(entri.seq)
        setelahTulis()
        return hasil.keHasilTulis()
    }
}
