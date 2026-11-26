package dev.kyle.mctools.shop

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
private fun Field(label: String, value: String, onChange: (String) -> Unit, lines: Int = 1, modifier: Modifier = Modifier) {
    OutlinedTextField(value = value, onValueChange = onChange, label = { Text(label) }, singleLine = lines == 1, minLines = lines, modifier = modifier.fillMaxWidth())
}

/** Keeps what the user is typing (like "1.") while only valid numbers are written to the file. */
@Composable
private fun NumField(label: String, current: Any?, modifier: Modifier = Modifier, onChange: (Double?) -> Unit) {
    var text by remember { mutableStateOf(current?.toString() ?: "") }
    OutlinedTextField(
        value = text, singleLine = true, label = { Text(label) }, modifier = modifier,
        onValueChange = { t -> text = t; if (t.isBlank()) onChange(null) else t.trim().toDoubleOrNull()?.let { onChange(it) } },
    )
}

private fun lines(v: Any?): String = (v as? List<*>)?.joinToString("\n") { it?.toString() ?: "" } ?: (v?.toString() ?: "")

@Composable
fun ShopEditorScreen(prefs: Prefs, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    var shop by remember { mutableStateOf<EsShop?>(null) }
    var section by remember { mutableStateOf<EsSection?>(null) }
    var includeSection by remember { mutableStateOf(false) }
    var page by remember { mutableStateOf("page1") }
    var open by remember { mutableStateOf<String?>(null) }
    var search by remember { mutableStateOf("") }
    var factor by remember { mutableStateOf("1.1") }
    var newMat by remember { mutableStateOf("") }
    var rev by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf<String?>(null) }
    var info by remember { mutableStateOf<String?>(null) }
    var saved by remember { mutableStateOf<ExportResult?>(null) }
    var source by remember { mutableStateOf("new shop") }
    var newName by remember { mutableStateOf("Blocks") }
    val ensureOutput = rememberEnsureOutput(prefs)
    val tick = rev
    fun changed() { rev++; saved = null }

    val pickShop = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) try {
            val (n, t) = EditorIO.readText(ctx, uri)
            val s = EsShop.parse(n.substringBeforeLast('.'), t)
            shop = s; source = n; page = s.pageNames().firstOrNull() ?: s.addPage(); open = null; saved = null; message = null; info = null
        } catch (e: YamlException) { message = "Couldn't read this shop. ${e.message}" }
        catch (e: ToolException) { message = e.userMessage }
        catch (e: Exception) { message = "Couldn't open that file." }
    }
    val pickSection = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) try {
            section = EsSection.parse(EditorIO.readText(ctx, uri).second); includeSection = true; message = null
        } catch (e: YamlException) { message = "Couldn't read this section file. ${e.message}" }
        catch (e: Exception) { message = "Couldn't open that file." }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.padding(20.dp, 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(cs.surface).clickable(role = Role.Button) { if (shop != null) shop = null else onBack() }, contentAlignment = Alignment.Center) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text("ShopGUI Editor", style = MaterialTheme.typography.titleLarge)
        }
        val s = shop
        if (s == null) {
            Column(Modifier.weight(1f).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Column(Modifier.fillMaxWidth().cardStyle().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("EconomyShopGUI shops", style = MaterialTheme.typography.titleSmall)
                    Text("Open a file from EconomyShopGUI/shops (v6 or Premium v5 layout), or start a new shop. Original files are never changed. Rotating shops aren't supported yet.", color = cs.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                }
                KButton("Open a shop file", { pickShop.launch(arrayOf("*/*")) }, Modifier.fillMaxWidth())
                Field("New shop name", newName, { newName = it })
                KButton("Create a new shop", { shop = EsShop.create(newName.ifBlank { "Shop" }); section = EsSection.create(newName.ifBlank { "Shop" }); includeSection = true; page = "page1"; source = "new shop"; saved = null; message = null }, Modifier.fillMaxWidth(), primary = false)
                message?.let { Text(it, color = cs.error) }
            }
        } else {
            val keys = s.items(page).keys.filter { k ->
                val it = s.item(page, k)
                search.isBlank() || k.contains(search, true) || (it?.get("material")?.toString()?.contains(search, true) == true) || (it?.get("name")?.toString()?.contains(search, true) == true)
            }
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    Column(Modifier.fillMaxWidth().cardStyle().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Field("Shop name (must match the section file name)", s.name, { s.name = it; changed() })
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            s.pageNames().forEach { p -> FilterChip(selected = p == page, onClick = { page = p; open = null }, label = { Text(p) }) }
                            FilterChip(selected = false, onClick = { page = s.addPage(); changed() }, label = { Text("+ page") })
                        }
                        Field("Page title", s.page(page)["title"]?.toString() ?: "", { s.page(page)["title"] = it; changed() })
                        Text("Rows", color = cs.onSurfaceVariant, fontSize = 14.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            (1..6).forEach { r -> FilterChip(selected = s.rows(page) == r, onClick = { s.setRows(page, r); changed() }, label = { Text("$r") }) }
                        }
                        if (s.pageNames().size > 1) SmallButton("Delete this page", { s.removePage(page); page = s.pageNames().first(); changed() }, color = cs.error)
                    }
                }
                item {
                    Column(Modifier.fillMaxWidth().cardStyle().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Change all prices", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                        Text("Multiplies buy and sell prices that are above 0. Prices of -1 (not buyable or sellable) stay.", color = cs.onSurfaceVariant, fontSize = 14.sp)
                        Field("Multiply by", factor, { factor = it })
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SmallButton("This page", { factor.toDoubleOrNull()?.let { f -> info = "Changed ${s.scalePrices(page, f)} prices on $page"; changed() } ?: run { info = "Enter a number like 1.25" } })
                            SmallButton("All pages", { factor.toDoubleOrNull()?.let { f -> info = "Changed ${s.scalePrices(null, f)} prices"; changed() } ?: run { info = "Enter a number like 1.25" } })
                        }
                        info?.let { Text(it, color = cs.primary, fontSize = 14.sp) }
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Field("Add item (material)", newMat, { newMat = it }, modifier = Modifier.weight(1f))
                        SmallButton("Add", { if (newMat.isNotBlank()) { open = s.addItem(page, newMat, 1.0, 0.5); newMat = ""; changed() } })
                    }
                }
                item { Field("Search items on this page", search, { search = it }) }
                items(keys, key = { it }) { k ->
                    val item = s.item(page, k)
                    if (item != null) {
                        val isOpen = open == k
                        Column(Modifier.fillMaxWidth().cardStyle().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(Modifier.fillMaxWidth().clickable(role = Role.Button) { open = if (isOpen) null else k }, verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(item["material"]?.toString() ?: "(no material)", fontWeight = FontWeight.Bold)
                                    Text("buy ${item["buy"] ?: "-"}   sell ${item["sell"] ?: "-"}   #$k", color = cs.onSurfaceVariant, fontSize = 14.sp)
                                }
                                Text(if (isOpen) "Close" else "Edit", color = cs.primary, fontSize = 14.sp)
                            }
                            if (isOpen) {
                                Field("Material", item["material"]?.toString() ?: "", { item["material"] = it.trim().uppercase(); changed() })
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    NumField("Buy", item["buy"], Modifier.weight(1f)) { d -> if (d == null) item.remove("buy") else item["buy"] = numValue(d); changed() }
                                    NumField("Sell", item["sell"], Modifier.weight(1f)) { d -> if (d == null) item.remove("sell") else item["sell"] = numValue(d); changed() }
                                }
                                Field("Name (on the bought item)", item["name"]?.toString() ?: "", { if (it.isEmpty()) item.remove("name") else item["name"] = it; changed() })
                                Field("Shop display name (optional)", item["displayname"]?.toString() ?: "", { if (it.isEmpty()) item.remove("displayname") else item["displayname"] = it; changed() })
                                Field("Lore (one line per row)", lines(item["lore"]), { t -> val l = t.lines().filter { x -> x.isNotEmpty() }; if (l.isEmpty()) item.remove("lore") else item["lore"] = l; changed() }, lines = 3)
                                Field("Slot (number, or ranges like 1-9,36-45)", item["slot"]?.toString() ?: "", { t -> val n = t.trim().toLongOrNull(); if (t.isBlank()) item.remove("slot") else item["slot"] = n ?: t; changed() })
                                val known = setOf("material", "buy", "sell", "name", "displayname", "lore", "slot")
                                val extra = item.keys.filter { x -> x !in known }
                                if (extra.isNotEmpty()) Text("Kept as they are: ${extra.joinToString()}", color = cs.onSurfaceVariant, fontSize = 14.sp)
                                SmallButton("Delete this item", { s.removeItem(page, k); open = null; changed() }, color = cs.error)
                            }
                        }
                    }
                }
                item {
                    val sec = section
                    Column(Modifier.fillMaxWidth().cardStyle().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Main menu button (section file)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                                Text("sections/${s.name}.yml", color = cs.onSurfaceVariant, fontSize = 14.sp)
                            }
                            Switch(checked = includeSection && sec != null, onCheckedChange = { on -> if (on && section == null) section = EsSection.create(s.name); includeSection = on; changed() })
                        }
                        if (includeSection && sec != null) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                NumField("Slot (0-44)", sec.slot, Modifier.weight(1f)) { d -> if (d != null) { sec.slot = d.toInt(); changed() } }
                                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Switch(checked = sec.enabled, onCheckedChange = { sec.enabled = it; changed() })
                                    Text("Enabled")
                                }
                            }
                            Field("Shop title", sec.title, { sec.title = it; changed() })
                            Field("Button material", sec.sub("item")["material"]?.toString() ?: "", { sec.sub("item")["material"] = it.trim().uppercase(); changed() })
                            Field("Button name", sec.sub("item")["name"]?.toString() ?: "", { sec.sub("item")["name"] = it; changed() })
                        }
                        SmallButton("Open an existing section file", { pickSection.launch(arrayOf("*/*")) })
                    }
                }
                item {
                    val warnings = s.validate() + (if (includeSection) section?.validate().orEmpty() else emptyList())
                    Column(Modifier.fillMaxWidth().cardStyle().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(if (warnings.isEmpty()) "No problems found" else "${warnings.size} thing(s) to check", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                        warnings.take(12).forEach { Text("- $it", color = cs.onSurfaceVariant, fontSize = 14.sp) }
                    }
                }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        KButton("Save to output folder", {
                            ensureOutput {
                                try {
                                    val id = s.name.replace(Regex("[^A-Za-z0-9_]+"), "_").ifEmpty { "Shop" }
                                    val files = LinkedHashMap<String, String>()
                                    files["shops/$id.yml"] = s.toYaml()
                                    val sec = section
                                    if (includeSection && sec != null) files["sections/$id.yml"] = sec.toYaml()
                                    saved = EditorIO.save(ctx, prefs.outputTree, "ShopGuiEditor", "ShopGUI Editor", id, files, "Saved ${files.keys.joinToString()} (${s.pageNames().sumOf { s.items(it).size }} items)", source)
                                    message = null
                                } catch (e: ToolException) { message = e.userMessage } catch (e: Exception) { message = "Couldn't save: ${e.message}" }
                            }
                        }, Modifier.fillMaxWidth())
                        message?.let { Text(it, color = cs.error) }
                        saved?.let { r ->
                            Column(Modifier.fillMaxWidth().cardStyle().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Saved", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                                Text(r.displayPath, color = cs.onSurfaceVariant, fontSize = 14.sp)
                                Text("Copy shops/ and sections/ into plugins/EconomyShopGUI/ and run /shopadmin reload. The file names of a shop and its section must match.", color = cs.onSurfaceVariant, fontSize = 14.sp)
                                KButton("Open output folder", { openFolder(ctx, r.folderUri, r.displayPath) }, Modifier.fillMaxWidth(), primary = false)
                            }
                        }
                        Box(Modifier.size(24.dp))
                    }
                }
            }
        }
    }
}
