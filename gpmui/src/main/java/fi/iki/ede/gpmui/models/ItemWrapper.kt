package fi.iki.ede.gpmui.models

data class ItemWrapper<T>(val item: T, var isSelected: Boolean = false)