package dev.kyle.mctools.menus

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.kyle.mctools.core.ToolException
import dev.kyle.mctools.core.YamlException
import dev.kyle.mctools.ui.EditorIO
import dev.kyle.mctools.ui.ExportResult
import dev.kyle.mctools.ui.KButton
import dev.kyle.mctools.ui.Prefs
import dev.kyle.mctools.ui.SmallButton
import dev.kyle.mctools.ui.cardStyle
import dev.kyle.mctools.ui.openFolder
import dev.kyle.mctools.ui.rememberEnsureOutput

@Composable
private fun Field(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier, lines: Int = 1) {
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) },
        singleLine = lines == 1, minLines = lines, modifier = modifier.fillMaxWidth(),
    )
}

@Composable
private fun Cell(slot: Int, material: String?, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier.aspectRatio(1f).clip(shape)
            .background(if (material != null) cs.primaryContainer else cs.surfaceVariant)
            .border(if (selected) 2.dp else 1.dp, if (selected) cs.primary else cs.outline, shape)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            material?.lowercase()?.take(4) ?: slot.toString(), fontSize = 11.sp,
            color = if (material != null) cs.onPrimaryContainer else cs.onSurfaceVariant,
        )
    }
}

@Composable
fun DeluxeMenusEditorScreen(prefs: Prefs, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    var menu by remember { mutableStateOf<DmMenu?>(null) }
    var rev by remember { mutableIntStateOf(0) }
    var selected by remember { mutableIntStateOf(-1) }
    var message by remember { mutableStateOf<String?>(null) }
    var saved by remember { mutableStateOf<ExportResult?>(null) }
    var source by remember { mutableStateOf("new menu") }
    var newName by remember { mutableStateOf("my_menu") }
    var fillerMat by remember { mutableStateOf("GRAY_STAINED_GLASS_PANE") }
    var fillerName by remember { mutableStateOf("") }
    val ensureOutput = rememberEnsureOutput(prefs)
    val tick = rev   // reading this makes the editor redraw after every change

    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                val (n, t) = EditorIO.readText(ctx, uri)
                menu = DmMenu.parse(n.substringBeforeLast('.'), t); source = n; selected = -1; saved = null; message = null
            } catch (e: YamlException) { message = "Couldn't read this menu. ${e.message}" }
            catch (e: ToolException) { message = e.userMessage }
            catch (e: Exception) { message = "Couldn't open that file." }
        }
    }
    fun changed() { rev++; saved = null }

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.padding(20.dp, 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(cs.surface).clickable(role = Role.Button) { if (menu != null) menu = null else onBack() }, contentAlignment = Alignment.Center) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text("DeluxeMenus Editor", style = MaterialTheme.typography.titleLarge)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            val m = menu
            if (m == null) {
                Column(Modifier.fillMaxWidth().cardStyle().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Edit a menu visually", style = MaterialTheme.typography.titleSmall)
                    Text("Open a file from DeluxeMenus/gui_menus, or start a new menu. Your original file is never changed; the edited copy is saved to your output folder.", color = cs.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                }
                KButton("Open a menu file", { pick.launch(arrayOf("*/*")) }, Modifier.fillMaxWidth())
                Field("New menu name", newName, { newName = it })
                KButton("Create a new menu", { menu = DmMenu.create(newName.ifBlank { "my_menu" }); source = "new menu"; selected = -1; saved = null; message = null }, Modifier.fillMaxWidth(), primary = false)
                message?.let { Text(it, color = cs.error) }
            } else {
                Column(Modifier.fillMaxWidth().cardStyle().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Field("File name (without .yml)", m.name, { m.name = it; changed() })
                    Field("Menu title", m.title, { m.title = it; changed() })
                    Field("Open command(s), comma separated", m.openCommand, { m.openCommand = it; changed() })
                    Text("Size", color = cs.onSurfaceVariant, fontSize = 14.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        (1..6).forEach { r ->
                            FilterChip(selected = m.rows == r && m.size % 9 == 0, onClick = { m.size = r * 9; m.trimFiller(); if (selected >= m.size) selected = -1; changed() }, label = { Text("$r") })
                        }
                    }
                }
                Column(Modifier.fillMaxWidth().cardStyle().padding(16.dp)) {
                    RequirementsEditor(m.root, DmReq.menuGroups) { changed() }
                }
                Text("Tap a slot to select it", color = cs.onSurfaceVariant, fontSize = 14.sp)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (r in 0 until m.rows) Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        for (c in 0 until 9) {
                            val slot = r * 9 + c
                            val key = m.keyAtSlot(slot)
                            val mat = key?.let { m.item(it)?.get("material")?.toString() ?: "?" }
                            Cell(slot, mat, selected == slot, Modifier.weight(1f)) { selected = slot }
                        }
                    }
                }

                Column(Modifier.fillMaxWidth().cardStyle().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Filler", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                    Text("Puts one item on every empty slot so the menu has no holes. Slots you fill later are taken out of the filler automatically.", color = cs.onSurfaceVariant, fontSize = 14.sp)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("GRAY_STAINED_GLASS_PANE", "BLACK_STAINED_GLASS_PANE", "WHITE_STAINED_GLASS_PANE", "LIGHT_GRAY_STAINED_GLASS_PANE", "BLUE_STAINED_GLASS_PANE").forEach { mat ->
                            FilterChip(selected = fillerMat == mat, onClick = { fillerMat = mat }, label = { Text(mat.removeSuffix("_STAINED_GLASS_PANE").lowercase()) })
                        }
                    }
                    Field("Filler material", fillerMat, { fillerMat = it.trim().uppercase() })
                    Field("Filler name (blank = hidden name)", fillerName, { fillerName = it })
                    val empties = m.emptySlots().size
                    KButton(if (empties > 0) "Fill $empties empty slots" else "No empty slots", { m.fillEmpty(fillerMat, fillerName); changed() }, Modifier.fillMaxWidth(), enabled = empties > 0)
                    if (m.fillerKey() != null) SmallButton("Remove filler", { m.removeFiller(); changed() }, color = cs.error)
                }

                if (selected in 0 until m.size) {
                    val key = m.keyAtSlot(selected)
                    val item = key?.let { m.item(it) }
                    Column(Modifier.fillMaxWidth().cardStyle(cs.primary.copy(alpha = 0.6f)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Slot $selected", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                        if (key == null || item == null) {
                            Text("This slot is empty.", color = cs.onSurfaceVariant)
                            KButton("Add an item here", { m.addItem(selected); changed() }, Modifier.fillMaxWidth())
                        } else if (m.isFillerKey(key)) {
                            Text("This slot is covered by the filler item.", color = cs.onSurfaceVariant)
                            KButton("Put a different item here", { m.addItem(selected); changed() }, Modifier.fillMaxWidth())
                            Text("Change the filler material or name in the Filler card above, then press Fill again.", color = cs.onSurfaceVariant, fontSize = 14.sp)
                        } else {
                            Field("Material", item["material"]?.toString() ?: "", { item["material"] = it.trim().uppercase(); changed() })
                            Field("Display name", item["display_name"]?.toString() ?: "", { if (it.isEmpty()) item.remove("display_name") else item["display_name"] = it; changed() })
                            Field("Lore (one line per row)", listOfLines(item["lore"]), { setLines(item, "lore", it); changed() }, lines = 3)
                            Field("Amount", item["amount"]?.toString() ?: "", { v -> v.toIntOrNull()?.let { item["amount"] = it.toLong() } ?: item.remove("amount"); changed() })
                            Field("Left click commands", listOfLines(item["left_click_commands"]), { setLines(item, "left_click_commands", it); changed() }, lines = 2)
                            Field("Right click commands", listOfLines(item["right_click_commands"]), { setLines(item, "right_click_commands", it); changed() }, lines = 2)
                            Field("Any click commands", listOfLines(item["click_commands"]), { setLines(item, "click_commands", it); changed() }, lines = 2)
                            RequirementsEditor(item, DmReq.itemGroups) { changed() }
                            val slots = m.slotsOf(item)
                            if (item.containsKey("slot") && !item.containsKey("slots")) {
                                Field("Slot", slots.firstOrNull()?.toString() ?: "", { v -> v.toIntOrNull()?.let { s -> m.setSlot(item, s); selected = s; changed() } })
                            } else Text("This item uses several slots: $slots", color = cs.onSurfaceVariant, fontSize = 14.sp)
                            val known = setOf("material", "display_name", "lore", "amount", "slot", "slots", "left_click_commands", "right_click_commands", "click_commands") + DmReq.itemGroups
                            val extra = item.keys.filter { it !in known }
                            if (extra.isNotEmpty()) Text("Kept as they are: ${extra.joinToString()}", color = cs.onSurfaceVariant, fontSize = 14.sp)
                            SmallButton("Delete this item", { m.removeItem(key); changed() }, color = cs.error)
                        }
                    }
                }

                val warnings = m.validate()
                Column(Modifier.fillMaxWidth().cardStyle().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(if (warnings.isEmpty()) "No problems found" else "${warnings.size} thing(s) to check", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                    warnings.take(12).forEach { Text("- $it", color = cs.onSurfaceVariant, fontSize = 14.sp) }
                }

                KButton("Save to output folder", {
                    ensureOutput {
                        try {
                            val id = m.name.lowercase().replace(Regex("[^a-z0-9_]+"), "_").ifEmpty { "menu" }
                            saved = EditorIO.save(ctx, prefs.outputTree, "DeluxeMenusEditor", "DeluxeMenus Editor", id,
                                mapOf("$id.yml" to m.toYaml(), "config_entry.yml" to m.configEntry()), "Saved $id.yml (${m.items.size} items)", source)
                            message = null
                        } catch (e: ToolException) { message = e.userMessage } catch (e: Exception) { message = "Couldn't save: ${e.message}" }
                    }
                }, Modifier.fillMaxWidth())
                message?.let { Text(it, color = cs.error) }
                saved?.let { r ->
                    Column(Modifier.fillMaxWidth().cardStyle().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Saved", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                        Text(r.displayPath, color = cs.onSurfaceVariant, fontSize = 14.sp)
                        Text("Put the .yml in DeluxeMenus/gui_menus/ and add the entry from config_entry.yml to config.yml, then /dm reload.", color = cs.onSurfaceVariant, fontSize = 14.sp)
                        KButton("Open output folder", { openFolder(ctx, r.folderUri, r.displayPath) }, Modifier.fillMaxWidth(), primary = false)
                    }
                }
            }
            Box(Modifier.size(24.dp))
        }
    }
}
