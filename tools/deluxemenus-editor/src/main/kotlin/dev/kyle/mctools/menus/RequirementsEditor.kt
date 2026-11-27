package dev.kyle.mctools.menus

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.kyle.mctools.ui.SmallButton
import dev.kyle.mctools.ui.cardStyle

@Composable
private fun TextRow(label: String, value: String, onChange: (String) -> Unit, lines: Int = 1) {
    OutlinedTextField(value = value, onValueChange = onChange, label = { Text(label) }, singleLine = lines == 1, minLines = lines, modifier = Modifier.fillMaxWidth())
}

/** Edits the requirement groups on [owner] (an item, or the menu itself for open_requirement). */
@Composable
fun RequirementsEditor(owner: YMap, groupKeys: List<String>, onChange: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Requirements", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
        for (gk in groupKeys) {
            val group = owner[gk] as? YMap ?: continue
            GroupCard(owner, gk, group, onChange)
        }
        val missing = groupKeys.filter { owner[it] !is Map<*, *> }
        if (missing.isNotEmpty()) {
            Text("Add requirements for...", fontSize = 14.sp, color = cs.onSurfaceVariant)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                missing.forEach { gk -> SmallButton("+ ${DmReq.groupLabel(gk)}", { owner[gk] = DmReq.newGroup(); onChange() }) }
            }
        }
    }
}

@Composable
private fun GroupCard(owner: YMap, gk: String, group: YMap, onChange: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val reqs = DmReq.requirementsOf(group)
    Column(Modifier.fillMaxWidth().cardStyle().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${DmReq.groupLabel(gk)} requirements", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            SmallButton("Remove", { owner.remove(gk); onChange() }, color = cs.error)
        }
        DmReq.commandsKeyFor(gk)?.let { Text("Needs $it on this item to work.", fontSize = 13.sp, color = cs.onSurfaceVariant) }
        reqs.keys.toList().forEachIndexed { idx, name -> key(idx) { RequirementCard(reqs, name, onChange) } }
        Text("Add a requirement", fontSize = 14.sp, color = cs.onSurfaceVariant)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            DmReq.types.forEach { t -> FilterChip(selected = false, onClick = { DmReq.addRequirement(group, t); onChange() }, label = { Text(t.label) }) }
        }
        if (reqs.size > 1) {
            TextRow("Minimum requirements to pass (optional)", group["minimum_requirements"]?.toString() ?: "", { v ->
                val n = v.trim().toLongOrNull()
                if (v.isBlank()) group.remove("minimum_requirements") else if (n != null) group["minimum_requirements"] = n
                onChange()
            })
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Switch(checked = group["stop_at_success"] == true, onCheckedChange = { if (it) group["stop_at_success"] = true else group.remove("stop_at_success"); onChange() })
                Text("Stop checking once enough are met")
            }
        }
        TextRow("Deny commands for the whole group (one per line)", listOfLines(group["deny_commands"]), { setLines(group, "deny_commands", it); onChange() }, lines = 2)
        TextRow("Success commands for the whole group (one per line)", listOfLines(group["success_commands"]), { setLines(group, "success_commands", it); onChange() }, lines = 2)
    }
}

@Composable
private fun RequirementCard(reqs: YMap, name: String, onChange: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val req = reqs[name] as? YMap ?: return
    var nameText by remember(name) { mutableStateOf(name) }
    val (negated, type) = DmReq.split(req["type"]?.toString().orEmpty())
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(cs.surfaceVariant).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = nameText, singleLine = true, label = { Text("Name") }, modifier = Modifier.weight(1f),
                onValueChange = { v -> nameText = v; if (DmReq.rename(reqs, name, v.trim())) onChange() },
            )
            SmallButton("Delete", { reqs.remove(name); onChange() }, color = cs.error)
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            DmReq.types.forEach { t ->
                FilterChip(selected = type?.id == t.id, onClick = { if (type?.id != t.id) { DmReq.setType(req, t, negated && t.negatable); onChange() } }, label = { Text(t.label) })
            }
        }
        if (type == null) {
            TextRow("Type", req["type"]?.toString() ?: "", { req["type"] = it; onChange() })
            Text("This type isn't in the editor's list. It is kept as it is.", fontSize = 13.sp, color = cs.onSurfaceVariant)
        } else {
            if (type.negatable) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Switch(checked = negated, onCheckedChange = { req["type"] = DmReq.typeString(type, it); onChange() })
                    Text("Must NOT be true (adds !)")
                }
            }
            for (f in type.fields) {
                TextRow(f.label + if (f.required) "" else " (optional)", req[f.key]?.toString() ?: "", { v -> DmReq.setScalar(req, f, v); onChange() })
            }
        }
        TextRow("Deny commands (one per line)", listOfLines(req["deny_commands"]), { setLines(req, "deny_commands", it); onChange() }, lines = 2)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Switch(checked = req["optional"] == true, onCheckedChange = { if (it) req["optional"] = true else req.remove("optional"); onChange() })
            Text("Optional (counts toward minimum)")
        }
        val known = setOf("type", "deny_commands", "success_commands", "optional") + (type?.fields?.map { it.key } ?: emptyList())
        val extra = req.keys.filter { it !in known }
        if (extra.isNotEmpty()) Text("Kept as they are: ${extra.joinToString()}", fontSize = 13.sp, color = cs.onSurfaceVariant)
    }
}
