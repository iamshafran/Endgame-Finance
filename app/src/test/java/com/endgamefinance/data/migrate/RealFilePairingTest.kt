package com.endgamefinance.data.migrate

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Diagnostic against the owner's actual Bluecoins export (local machine only —
 * skipped when the file isn't present, e.g. on CI).
 */
class RealFilePairingTest {

    private val path = "C:\\Users\\Shafran\\Documents\\Sync\\Transactions.csv"

    private val mapping = ColumnMapping(
        type = 0, date = 1, payee = 3, amount = 4,
        categoryGroup = 7, category = 8, account = 9,
        notes = 10, labels = 11, status = 12,
    )

    @Test
    fun real_export_pairs_all_transfers() {
        val file = File(path)
        assumeTrue("sample not on this machine", file.exists())

        val rows = CsvParser.parse(file.readText())
        val header = rows.first()
        println("HEADER: $header")

        val warnings = mutableListOf<String>()
        val normalized = BluecoinsImport.normalize(mapping, rows.drop(1), warnings)
        val paired = BluecoinsImport.pairTransfers(normalized)

        println("rows=${normalized.size} plain=${paired.plain.size} " +
            "transfers=${paired.transfers.size} unpaired=${paired.unpaired.size} " +
            "starting=${paired.startingBalances.size} warnings=${warnings.size}")
        warnings.forEach { println("WARN: $it") }
        paired.unpaired.forEach {
            println("UNPAIRED: ${it.payee} ${it.amountCents} ${it.account} ts=${it.timestamp}")
        }
        paired.transfers.forEach {
            println("PAIR: '${it.source.payee}' ${it.source.account} -> ${it.destination.account} ${it.source.amountCents}")
        }

        // The sample has 12 transfer pairs (24 legs) and 6 starting balances.
        assertEquals(0, paired.unpaired.size)
    }
}
