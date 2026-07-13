package com.endgamefinance.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.BeachAccess
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.BusinessCenter
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.Chair
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.Church
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.FamilyRestroom
import androidx.compose.material.icons.filled.Fastfood
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Healing
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Hotel
import androidx.compose.material.icons.filled.Icecream
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.LocalBar
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.LocalGroceryStore
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.LocalLaundryService
import androidx.compose.material.icons.filled.LocalMall
import androidx.compose.material.icons.filled.LocalParking
import androidx.compose.material.icons.filled.LocalPizza
import androidx.compose.material.icons.filled.LocalTaxi
import androidx.compose.material.icons.filled.Luggage
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Park
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Pool
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.Redeem
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.SportsBasketball
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Theaters
import androidx.compose.material.icons.filled.Train
import androidx.compose.material.icons.filled.Umbrella
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.Work
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Curated, searchable subset of the Material icon set for categories.
 * Keys are stored in categories.icon — never rename a key without a migration.
 */
object IconCatalog {

    val entries: List<Pair<String, ImageVector>> = listOf(
        // Food & drink
        "restaurant" to Icons.Filled.Restaurant,
        "fastfood" to Icons.Filled.Fastfood,
        "cafe" to Icons.Filled.LocalCafe,
        "bar" to Icons.Filled.LocalBar,
        "pizza" to Icons.Filled.LocalPizza,
        "icecream" to Icons.Filled.Icecream,
        "cake" to Icons.Filled.Cake,
        "groceries" to Icons.Filled.LocalGroceryStore,
        // Transport
        "car" to Icons.Filled.DirectionsCar,
        "bus" to Icons.Filled.DirectionsBus,
        "train" to Icons.Filled.Train,
        "taxi" to Icons.Filled.LocalTaxi,
        "flight" to Icons.Filled.Flight,
        "gas" to Icons.Filled.LocalGasStation,
        "parking" to Icons.Filled.LocalParking,
        // Home & utilities
        "home" to Icons.Filled.Home,
        "furniture" to Icons.Filled.Chair,
        "tools" to Icons.Filled.Build,
        "electricity" to Icons.Filled.Bolt,
        "power" to Icons.Filled.Power,
        "light" to Icons.Filled.Lightbulb,
        "wifi" to Icons.Filled.Wifi,
        "phone" to Icons.Filled.Phone,
        "mobile" to Icons.Filled.PhoneAndroid,
        "laundry" to Icons.Filled.LocalLaundryService,
        // Health & fitness
        "hospital" to Icons.Filled.LocalHospital,
        "healing" to Icons.Filled.Healing,
        "fitness" to Icons.Filled.FitnessCenter,
        "spa" to Icons.Filled.Spa,
        // Shopping & personal
        "cart" to Icons.Filled.ShoppingCart,
        "shopping" to Icons.Filled.ShoppingBag,
        "mall" to Icons.Filled.LocalMall,
        "clothes" to Icons.Filled.Checkroom,
        "haircut" to Icons.Filled.ContentCut,
        "beauty" to Icons.Filled.Brush,
        "face" to Icons.Filled.Face,
        "watch" to Icons.Filled.Watch,
        "gift" to Icons.Filled.CardGiftcard,
        "redeem" to Icons.Filled.Redeem,
        // Entertainment & leisure
        "movie" to Icons.Filled.Movie,
        "theater" to Icons.Filled.Theaters,
        "music" to Icons.Filled.MusicNote,
        "games" to Icons.Filled.SportsEsports,
        "soccer" to Icons.Filled.SportsSoccer,
        "basketball" to Icons.Filled.SportsBasketball,
        "casino" to Icons.Filled.Casino,
        "park" to Icons.Filled.Park,
        "beach" to Icons.Filled.BeachAccess,
        "pool" to Icons.Filled.Pool,
        "celebration" to Icons.Filled.Celebration,
        "camera" to Icons.Filled.CameraAlt,
        // Travel
        "luggage" to Icons.Filled.Luggage,
        "hotel" to Icons.Filled.Hotel,
        "map" to Icons.Filled.Map,
        // Education & work
        "school" to Icons.Filled.School,
        "science" to Icons.Filled.Science,
        "work" to Icons.Filled.Work,
        "business" to Icons.Filled.BusinessCenter,
        // Money
        "money" to Icons.Filled.AttachMoney,
        "bank" to Icons.Filled.AccountBalance,
        "wallet" to Icons.Filled.AccountBalanceWallet,
        "card" to Icons.Filled.CreditCard,
        "savings" to Icons.Filled.Savings,
        // Family & pets
        "pets" to Icons.Filled.Pets,
        "child" to Icons.Filled.ChildCare,
        "family" to Icons.Filled.FamilyRestroom,
        // Misc
        "insurance" to Icons.Filled.Security,
        "umbrella" to Icons.Filled.Umbrella,
        "church" to Icons.Filled.Church,
        "favorite" to Icons.Filled.Favorite,
        "star" to Icons.Filled.Star,
        "category" to Icons.Filled.Category,
    )

    private val byKey = entries.toMap()

    fun get(key: String?): ImageVector? = key?.let { byKey[it] }

    fun search(query: String): List<Pair<String, ImageVector>> =
        if (query.isBlank()) entries
        else entries.filter { (key, _) -> key.contains(query.trim().lowercase()) }
}
