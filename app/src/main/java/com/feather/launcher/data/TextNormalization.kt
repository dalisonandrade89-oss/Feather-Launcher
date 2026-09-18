package com.feather.launcher.data

import java.text.Normalizer

/**
 * Remove acentos/diacríticos e converte para minúsculas, para permitir
 * que a busca encontre "ça" digitando "ca", "ó" digitando "o", etc.
 * Operação leve (regex simples), segura para rodar por item da lista.
 */
fun String.toSearchNormalized(): String {
    val decomposed = Normalizer.normalize(this, Normalizer.Form.NFD)
    return DIACRITICS_REGEX.replace(decomposed, "").lowercase()
}

private val DIACRITICS_REGEX = Regex("\\p{Mn}+")
