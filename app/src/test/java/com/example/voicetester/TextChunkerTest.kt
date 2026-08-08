package com.example.voicetester

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 分割は「機械音声らしさ」の中核なので、JS 版と同じ結果になることを固定しておく。
 * Kotlin の Regex.split は末尾のゼロ幅マッチで空文字を余分に生むが、
 * chunkText 側の filter で落ちるため JS と同じ出力になる — それもここで担保する。
 */
class TextChunkerTest {

    @Test
    fun `句点で分割し区切り文字は末尾に残る`() {
        assertEquals(
            listOf("こんにちは。", "私の名前はドラムです。"),
            chunkText("こんにちは。私の名前はドラムです。", flatten = true),
        )
    }

    @Test
    fun `読点でも分割する`() {
        assertEquals(
            listOf("はい、", "そうです。"),
            chunkText("はい、そうです。", flatten = true),
        )
    }

    @Test
    fun `全角半角の感嘆符と疑問符で分割する`() {
        assertEquals(
            listOf("え！", "本当？", "うそ!", "まじ?"),
            chunkText("え！本当？うそ!まじ?", flatten = true),
        )
    }

    @Test
    fun `半角カンマとピリオドで分割する`() {
        assertEquals(
            listOf("a,", "b.", "c．", "d"),
            chunkText("a,b.c．d", flatten = true),
        )
    }

    @Test
    fun `改行でも分割する`() {
        assertEquals(
            listOf("一行目", "二行目"),
            chunkText("一行目\n二行目", flatten = true),
        )
    }

    @Test
    fun `区切りで終わらない末尾も最後のチャンクとして残る`() {
        assertEquals(
            listOf("はい。", "まだ途中"),
            chunkText("はい。まだ途中", flatten = true),
        )
    }

    @Test
    fun `末尾が区切り文字でも空チャンクを生まない`() {
        assertEquals(
            listOf("おわり。"),
            chunkText("おわり。", flatten = true),
        )
    }

    @Test
    fun `連続した区切り文字で空チャンクを生まない`() {
        assertEquals(
            listOf("えっ。", "。", "。"),
            chunkText("えっ。。。", flatten = true),
        )
    }

    @Test
    fun `各チャンクの前後の空白は落とす`() {
        assertEquals(
            listOf("はい。", "そう。"),
            chunkText("はい。  そう。", flatten = true),
        )
    }

    @Test
    fun `空文字や空白のみなら空リスト`() {
        assertEquals(emptyList<String>(), chunkText("", flatten = true))
        assertEquals(emptyList<String>(), chunkText("   \n  ", flatten = true))
        assertEquals(emptyList<String>(), chunkText("   ", flatten = false))
    }

    @Test
    fun `平坦化オフなら全文が一つのチャンクになる`() {
        assertEquals(
            listOf("こんにちは。私の名前はドラムです。"),
            chunkText("  こんにちは。私の名前はドラムです。  ", flatten = false),
        )
    }
}
