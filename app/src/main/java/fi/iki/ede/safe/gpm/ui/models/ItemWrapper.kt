package fi.iki.ede.safe.gpm.ui.models

data class ItemWrapper<T>(val item: T, var isSelected: Boolean = false)