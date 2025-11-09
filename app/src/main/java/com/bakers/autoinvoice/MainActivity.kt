package com.bakers.autoinvoice

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AutoInvoiceApp() }
    }
}

/* ---------------------------- Models ---------------------------- */

data class Customer(var name: String = "", var phone: String = "", var email: String = "")
data class Vehicle(var year: String = "", var make: String = "", var model: String = "", var vin: String = "")
data class LineItem(var description: String = "", var qty: BigDecimal = BigDecimal.ONE, var unit: BigDecimal = BigDecimal.ZERO)
data class Invoice(
    var number: String = SimpleDateFormat("yyyyMMdd-HHmm", Locale.getDefault()).format(Date()),
    var customer: Customer = Customer(),
    var vehicle: Vehicle = Vehicle(),
    var laborHours: BigDecimal = BigDecimal.ZERO,
    var laborRate: BigDecimal = BigDecimal("110.00"),
    var taxPercent: BigDecimal = BigDecimal("6.00"),
    var items: MutableList<LineItem> = mutableListOf(LineItem())
)

private fun BigDecimal.money(): String = NumberFormat.getCurrencyInstance().format(this)

/* --------------------------- Navigation ------------------------- */

private enum class Screen { HOME, NEW_INVOICE, HISTORY, SETTINGS }

@Composable
fun AutoInvoiceApp() {
    var screen by remember { mutableStateOf(Screen.HOME) }
    var current by remember { mutableStateOf(Invoice()) }

    MaterialTheme {
        Scaffold(
            topBar = {
                TopAppBar(title = {
                    Text(
                        when (screen) {
                            Screen.HOME -> "AutoInvoice"
                            Screen.NEW_INVOICE -> "New Invoice"
                            Screen.HISTORY -> "Saved Invoices"
                            Screen.SETTINGS -> "Settings"
                        }
                    )
                })
            }
        ) { inner ->
            Box(Modifier.padding(inner)) {
                when (screen) {
                    Screen.HOME -> HomeScreen(
                        onNewInvoice = { current = Invoice(); screen = Screen.NEW_INVOICE },
                        onHistory = { screen = Screen.HISTORY },
                        onSettings = { screen = Screen.SETTINGS }
                    )
                    Screen.NEW_INVOICE -> NewInvoiceScreen(
                        invoice = current,
                        onUpdate = { current = it },
                        onBack = { screen = Screen.HOME },
                        onSaved = { screen = Screen.HOME }
                    )
                    Screen.HISTORY -> PlaceholderScreen(
                        text = "History coming soon — we’ll add Room persistence next.",
                        onBack = { screen = Screen.HOME }
                    )
                    Screen.SETTINGS -> PlaceholderScreen(
                        text = "Settings coming soon — shop name, labor rate, tax, logo, etc.",
                        onBack = { screen = Screen.HOME }
                    )
                }
            }
        }
    }
}

/* --------------------------- Screens ---------------------------- */

@Composable
fun HomeScreen(
    onNewInvoice: () -> Unit,
    onHistory: () -> Unit,
    onSettings: () -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Baker's Auto — Invoice App", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Button(onClick = onNewInvoice, modifier = Modifier.fillMaxWidth()) { Text("➕ New Invoice") }
        Button(onClick = onHistory, modifier = Modifier.fillMaxWidth()) { Text("🗂 Saved Invoices") }
        Button(onClick = onSettings, modifier = Modifier.fillMaxWidth()) { Text("⚙️ Settings") }
    }
}

@Composable
fun PlaceholderScreen(text: String, onBack: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(text)
        OutlinedButton(onClick = onBack) { Text("Back") }
    }
}

@Composable
fun NewInvoiceScreen(
    invoice: Invoice,
    onUpdate: (Invoice) -> Unit,
    onBack: () -> Unit,
    onSaved: () -> Unit
) {
    val ctx = LocalContext.current

    // Totals
    val parts = remember(invoice) {
        invoice.items.fold(BigDecimal.ZERO) { acc, li ->
            acc + (li.qty.multiply(li.unit).setScale(2, RoundingMode.HALF_UP))
        }
    }
    val labor = remember(invoice) {
        invoice.laborHours.multiply(invoice.laborRate).setScale(2, RoundingMode.HALF_UP)
    }
    val taxable = remember(parts, labor) { (parts + labor).setScale(2, RoundingMode.HALF_UP) }
    val tax = remember(invoice, taxable) {
        taxable.multiply(invoice.taxPercent).divide(BigDecimal("100"))
            .setScale(2, RoundingMode.HALF_UP)
    }
    val grand = remember(taxable, tax) { (taxable + tax).setScale(2, RoundingMode.HALF_UP) }

    fun update(block: Invoice.() -> Unit) {
        val copy = invoice.copy(
            customer = invoice.customer.copy(),
            vehicle = invoice.vehicle.copy(),
            items = invoice.items.map { it.copy() }.toMutableList()
        )
        copy.block()
        onUpdate(copy)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Invoice # ${invoice.number}", fontWeight = FontWeight.SemiBold)
                OutlinedButton(onClick = onBack) { Text("Back") }
            }
        }

        // Customer
        item { SectionTitle("Customer") }
        item {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = invoice.customer.name,
                    onValueChange = { update { customer = customer.copy(name = it) } },
                    label = { Text("Name") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = invoice.customer.phone,
                    onValueChange = { update { customer = customer.copy(phone = it) } },
                    label = { Text("Phone") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
                )
                OutlinedTextField(
                    value = invoice.customer.email,
                    onValueChange = { update { customer = customer.copy(email = it) } },
                    label = { Text("Email") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
                )
            }
        }

        // Vehicle
        item { SectionTitle("Vehicle") }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = invoice.vehicle.year,
                    onValueChange = { update { vehicle = vehicle.copy(year = it) } },
                    label = { Text("Year") },
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                OutlinedTextField(
                    value = invoice.vehicle.make,
                    onValueChange = { update { vehicle = vehicle.copy(make = it) } },
                    label = { Text("Make") },
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = invoice.vehicle.model,
                    onValueChange = { update { vehicle = vehicle.copy(model = it) } },
                    label = { Text("Model") },
                    modifier = Modifier.weight(1f)
                )
            }
        }
        item {
            OutlinedTextField(
                value = invoice.vehicle.vin,
                onValueChange = { update { vehicle = vehicle.copy(vin = it.uppercase(Locale.US)) } },
                label = { Text("VIN") },
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Labor + Rates
        item { SectionTitle("Labor") }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MoneyField("Labor Rate (per hr)", invoice.laborRate) { v -> update { laborRate = v } }
                DecimalField("Hours", invoice.laborHours) { v -> update { laborHours = v } }
                PercentField("Tax %", invoice.taxPercent) { v -> update { taxPercent = v } }
            }
        }

        // Line Items
        item { SectionTitle("Parts / Services") }
        itemsIndexed(invoice.items) { idx, li ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = li.description,
                        onValueChange = { v -> update { items[idx] = items[idx].copy(description = v) } },
                        label = { Text("Description") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DecimalField("Qty", li.qty) { v -> update { items[idx] = items[idx].copy(qty = v) } }
                        MoneyField("Unit", li.unit) { v -> update { items[idx] = items[idx].copy(unit = v) } }
                        Text(
                            "Line: ${(li.qty.multiply(li.unit)).setScale(2, RoundingMode.HALF_UP).money()}",
                            modifier = Modifier.align(Alignment.CenterVertically)
                        )
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(
                            onClick = {
                                update {
                                    if (items.size > 1) items.removeAt(idx)
                                }
                            }
                        ) { Text("Remove") }
                    }
                }
            }
        }
        item {
            OutlinedButton(onClick = { update { items.add(LineItem()) } }, modifier = Modifier.fillMaxWidth()) {
                Text("Add Line Item")
            }
        }

        // Totals + Save
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Summary", fontWeight = FontWeight.SemiBold)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Parts"); Text(parts.money())
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Labor"); Text(labor.money())
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Tax (${invoice.taxPercent.stripTrailingZeros().toPlainString()}%)"); Text(tax.money())
                    }
                    Divider()
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Grand Total", fontWeight = FontWeight.Bold); Text(grand.money(), fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = {
                            Toast.makeText(ctx, "Invoice saved (in-memory demo).", Toast.LENGTH_SHORT).show()
                            onSaved()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Save") }
                }
            }
        }
        item { Spacer(Modifier.height(48.dp)) }
    }
}

/* ----------------------- Reusable fields ------------------------ */

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun DecimalField(label: String, value: BigDecimal, onChange: (BigDecimal) -> Unit) {
    var t by remember(value) { mutableStateOf(value.stripTrailingZeros().toPlainString()) }
    OutlinedTextField(
        value = t,
        onValueChange = {
            t = it
            it.toBigDecimalOrNull()?.let(onChange)
        },
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.weight(1f)
    )
}

@Composable
private fun MoneyField(label: String, value: BigDecimal, onChange: (BigDecimal) -> Unit) {
    var t by remember(value) { mutableStateOf(value.setScale(2, RoundingMode.HALF_UP).toPlainString()) }
    OutlinedTextField(
        value = t,
        onValueChange = {
            t = it
            it.toBigDecimalOrNull()?.setScale(2, RoundingMode.HALF_UP)?.let(onChange)
        },
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.weight(1f)
    )
}

@Composable
private fun PercentField(label: String, value: BigDecimal, onChange: (BigDecimal) -> Unit) {
    var t by remember(value) { mutableStateOf(value.stripTrailingZeros().toPlainString()) }
    OutlinedTextField(
        value = t,
        onValueChange = {
            t = it
            it.toBigDecimalOrNull()?.let(onChange)
        },
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.weight(1f)
    )
}
