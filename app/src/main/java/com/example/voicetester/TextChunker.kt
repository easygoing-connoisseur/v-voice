package com.example.voicetester

/**
 * 句読点の「直後」で切るゼロ幅後読み。区切り文字は前のチャンクの末尾に残る。
 *
 * 文字クラスの中なので `.` `?` `!` はエスケープ不要（リテラル扱い）。
 */
private val BOUNDARY = Regex("(?<=[。、！？!?,．.\n])")

/**
 * 読み上げ用に文を分割する。voice-tester.html の `chunk()` の移植。
 *
 * 文を細切れにして 1 チャンクずつ発話させると、TTS が文全体に対して持っている
 * 自然な抑揚カーブが断ち切られる。これが「翻訳アプリらしい平坦な喋り」の主因で、
 * ピッチを下げること以上に効いている。
 *
 * @param flatten false なら分割せず全文を 1 チャンクとして返す
 */
fun chunkText(text: String, flatten: Boolean): List<String> {
    if (!flatten) {
        val whole = text.trim()
        return if (whole.isEmpty()) emptyList() else listOf(whole)
    }
    return text.split(BOUNDARY)
        .map { it.trim() }
        .filter { it.isNotEmpty() }
}
