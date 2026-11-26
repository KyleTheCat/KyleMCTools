package dev.kyle.mctools

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Star
import androidx.compose.ui.graphics.vector.ImageVector

/** Phase = the build phase in which the tool ships. Tools from later phases show as "coming soon". */
enum class ToolId(val title: String, val subtitle: String, val icon: ImageVector, val phase: Int, val folder: String) {
    GEYSER("Java to Bedrock", "Convert resource packs and make the Geyser files.", Icons.Default.Refresh, 1, "JavaToBedrock"),
    PLUGIN("Pack to plugin config", "Make Nexo, ItemsAdder or Oraxen config from a pack.", Icons.AutoMirrored.Filled.List, 1, "PackToPluginConfig"),
    OPTIMIZER("Pack Optimizer", "Shrink Java and Bedrock packs.", Icons.Default.Build, 2, "PackOptimizer"),
    PROTECTOR("Pack Protector", "Make packs harder to copy.", Icons.Default.Lock, 2, "PackProtector"),
    MEG("MEG Converter", "Turn .bbmodel files into Bedrock models.", Icons.Default.Face, 3, "MegConverter"),
    VERSION("Version Converter", "Move a pack to another Minecraft version.", Icons.Default.DateRange, 3, "VersionConverter"),
    MENUS("DeluxeMenus Editor", "Edit GUI menus visually.", Icons.Default.Menu, 4, "DeluxeMenusEditor"),
    SHOP("ShopGUI Editor", "Edit shops, sections and prices.", Icons.Default.ShoppingCart, 4, "ShopGuiEditor"),
    RANKS("Rank Prefix Editor", "Edit rank prefixes with image previews.", Icons.Default.Star, 5, "RankPrefixEditor");

    val available: Boolean get() = phase <= 4
}
