package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun KeyValueEditor(
    title: String,
    items: List<Pair<String, String>>,
    onItemChange: (index: Int, key: String, value: String) -> Unit,
    onItemDelete: (index: Int) -> Unit,
    onItemAdd: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF1E293B), RoundedCornerShape(12.dp))
            .border(1.dp, Color(0xFF334155), RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFF1F5F9)
            )

            OutlinedButton(
                onClick = onItemAdd,
                modifier = Modifier
                    .height(34.dp)
                    .testTag("add_item_${title.lowercase().replace(" ", "_")}"),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF38BDF8)),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add", modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("ADD", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (items.isEmpty()) {
            Text(
                text = "No entries defined.",
                fontSize = 12.sp,
                color = Color(0xFF64748B),
                modifier = Modifier.padding(vertical = 8.dp)
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items.forEachIndexed { index, (key, value) ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedTextField(
                            value = key,
                            onValueChange = { newKey -> onItemChange(index, newKey, value) },
                            placeholder = { Text("Key", fontSize = 12.sp) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("key_field_$index"),
                            singleLine = true,
                            textStyle = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                color = Color.White
                            ),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF38BDF8),
                                unfocusedBorderColor = Color(0xFF475569)
                            )
                        )

                        OutlinedTextField(
                            value = value,
                            onValueChange = { newVal -> onItemChange(index, key, newVal) },
                            placeholder = { Text("Value", fontSize = 12.sp) },
                            modifier = Modifier
                                .weight(1.3f)
                                .testTag("val_field_$index"),
                            singleLine = true,
                            textStyle = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                color = Color(0xFFCBD5E1)
                            ),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF38BDF8),
                                unfocusedBorderColor = Color(0xFF475569)
                            )
                        )

                        IconButton(
                            onClick = { onItemDelete(index) },
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("delete_item_$index")
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Delete",
                                tint = Color(0xFFEF4444),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
