package com.ivy.core.ui.data

import androidx.annotation.ColorInt
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import com.ivy.core.ui.R
import com.ivy.core.ui.data.icon.ItemIcon
import com.ivy.core.ui.data.icon.dummyIconSized
import com.ivy.design.l0_system.color.Purple

@Immutable
data class CategoryUi(
    val id: String,
    val name: String,
    val color: Color,
    val icon: ItemIcon,
)

fun dummyCategoryUi(
    name: String = "Category",
    @ColorInt
    color: Color = Purple,
    icon: ItemIcon = dummyIconSized(R.drawable.ic_custom_category_s)
) = CategoryUi(
    id = "",
    name = name,
    color = color,
    icon = icon,
)