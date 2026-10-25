package com.lifepilot.app.ui.common

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.FamilyRestroom
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LocalDrink
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.SelfImprovement
import androidx.compose.material.icons.rounded.ShoppingCart
import androidx.compose.material.icons.rounded.Spa
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Work
import androidx.compose.ui.graphics.vector.ImageVector

/** Icon keys stored in the database, mapped to Material icons. */
object AppIcons {
    val categoryIcons: Map<String, ImageVector> = linkedMapOf(
        "work" to Icons.Rounded.Work,
        "learning" to Icons.Rounded.School,
        "health" to Icons.Rounded.FitnessCenter,
        "personal" to Icons.Rounded.Person,
        "family" to Icons.Rounded.FamilyRestroom,
        "finance" to Icons.Rounded.AccountBalanceWallet,
        "other" to Icons.Rounded.Category,
        "home" to Icons.Rounded.Home,
        "shopping" to Icons.Rounded.ShoppingCart,
        "code" to Icons.Rounded.Code,
        "creative" to Icons.Rounded.Brush,
        "heart" to Icons.Rounded.Favorite,
        "star" to Icons.Rounded.Star,
    )

    val habitIcons: Map<String, ImageVector> = linkedMapOf(
        "book" to Icons.AutoMirrored.Rounded.MenuBook,
        "study" to Icons.Rounded.School,
        "exercise" to Icons.Rounded.FitnessCenter,
        "walk" to Icons.AutoMirrored.Rounded.DirectionsWalk,
        "journal" to Icons.Rounded.EditNote,
        "meditate" to Icons.Rounded.SelfImprovement,
        "water" to Icons.Rounded.LocalDrink,
        "sleep" to Icons.Rounded.Bedtime,
        "music" to Icons.Rounded.MusicNote,
        "code" to Icons.Rounded.Code,
        "wellness" to Icons.Rounded.Spa,
        "heart" to Icons.Rounded.Favorite,
    )

    fun category(key: String?): ImageVector = categoryIcons[key] ?: Icons.Rounded.Category
    fun habit(key: String?): ImageVector = habitIcons[key] ?: Icons.Rounded.Star
}
