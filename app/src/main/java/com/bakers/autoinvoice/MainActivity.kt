package com.bakers.autoinvoice

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Locale

// ---------------------------- Activity ----------------------------

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AutoInvoiceApp() }
    }
}

// ----------------------------- Models -----------------------------

private enum class Screen { HOME, NEW }

data class Customer(
    var name: String = "",
    var phone: String = "",
    var email: String = ""
)

data class Vehicle(
    var year: String = "",
    var make: String = "",
    var model: String = "",
    var vin: String = ""
)

data class LineItem(
    var description: String = "",
    var qty: BigDecimal = BigDecimal.ONE,
    var unit: BigDecimal = BigDecimal.ZERO
)

data class Invoice(
    var number: String = java.text.SimpleDateFormat(
        "yyyyMMdd-HHmm",
        Locale.getDefault()
    ).format(java.util.Date()),
    var customer: Customer = Customer(),
    var vehicle: Vehicle = Vehicle(),
    var laborHours: BigDecimal = BigDecimal.ZERO,
    var laborRate: BigDecimal = BigDecimal("150.00"),
    var taxPercent: BigDecimal = BigDecimal("6.00"),
    var shopFee: BigDecimal = BigDecimal("5.00"),
    var items: MutableList<LineItem> = mutableListOf(LineItem())
)

// Format BigDecimal → money
private fun BigDecimal.money(): String =
    NumberFormat.getCurrencyInstance().format(this)

// ----------------------------- App Root ----------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutoInvoiceApp() {
    var screen by remember { mutableStateOf(Screen.HOME) }
    var inv by remember { mutableStateOf(Invoice()) }

    MaterialTheme {
        Scaffold(
            topBar = {
                SmallTopAppBar(
                    title = {
                        Text(if (screen == Screen.HOME) "AutoInvoice" else "New Invoice")
                    }
                )
            }
        ) { inner ->
            Box(modifier = Modifier.padding(inner)) {
                when (screen) {
                    Screen.HOME -> HomeScreen(
                        onNew = {
                            inv = Invoice()
                            screen = Screen.NEW
                        }
                    )
                    Screen.NEW -> NewInvoiceScreen(
                        invoice = inv,
                        onUpdate = { inv = it },
                        onBack = { screen = Screen.HOME }
                    )
                }
            }
        }
    }
}

// --------------------------- Home Screen --------------------------

@Composable
private fun HomeScreen(onNew: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Baker's Auto — Invoice App", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onNew, modifier = Modifier.fillMaxWidth()) {
            Text("➕ New Invoice")
        }
    }
}

// ------------------------ New Invoice Screen ----------------------

@Composable
private fun NewInvoiceScreen(
    invoice: Invoice,
    onUpdate: (Invoice) -> Unit,
    onBack: () -> Unit
) {
    fun update(block: Invoice.() -> Unit) {
        val copy = invoice.copy(
            customer = invoice.customer.copy(),
            vehicle = invoice.vehicle.copy(),
            items = invoice.items.map { it.copy() }.toMutableList()
        )
        copy.block()
        onUpdate(copy)
    }

    // Calculations
    val parts = invoice.items.fold(BigDecimal.ZERO) { acc, li ->
        acc + (li.qty * li.unit).setScale(2, RoundingMode.HALF_UP)
    }

    val labor = invoice.laborHours.multiply(invoice.laborRate).setScale(2, RoundingMode.HALF_UP)
    val shopFee = invoice.shopFee.setScale(2, RoundingMode.HALF_UP)
    val tax = parts.multiply(invoice.taxPercent).divide(BigDecimal("100"))
        .setScale(2, RoundingMode.HALF_UP)
    val grand = (parts + labor + shopFee + tax).setScale(2, RoundingMode.HALF_UP)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {

        item {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Invoice # ${invoice.number}", style = MaterialTheme.typography.titleMedium)
                OutlinedButton(onClick = onBack) { Text("Back") }
            }
        }

        // -------------------- CUSTOMER --------------------
        item { SectionTitle("Customer") }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SimpleField("Name", invoice.customer.name) {
                    update { customer = customer.copy(name = it) }
                }
                SimpleField("Phone", invoice.customer.phone) {
                    update { customer = customer.copy(phone = it) }
                }
                SimpleField("Email", invoice.customer.email) {
                    update { customer = customer.copy(email = it) }
                }
            }
        }

        // -------------------- VEHICLE --------------------
        item { SectionTitle("Vehicle") }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SimpleField("Year", invoice.vehicle.year) {
                    update { vehicle = vehicle.copy(year = it) }
                }
                SimpleField("Make", invoice.vehicle.make) {
                    update { vehicle = vehicle.copy(make = it) }
                }
                SimpleField("Model", invoice.vehicle.model) {
                    update { vehicle = vehicle.copy(model = it) }
                }
                SimpleField("VIN", invoice.vehicle.vin) {
                    update { vehicle = vehicle.copy(vin = it.uppercase()) }
                }
            }
        }

        // -------------------- LABOR --------------------
        item { SectionTitle("Labor") }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MoneyField("Rate/hr", invoice.laborRate) {
                    update { laborRate = it }
                }
                DecimalField("Hours", invoice.laborHours) {
                    update { laborHours = it }
                }
                PercentField("Tax % (parts only)", invoice.taxPercent) {
                    update { taxPercent = it }
                }
            }
        }

        // -------------------- PARTS / SERVICES --------------------
        item { SectionTitle("Parts / Services") }

        itemsIndexed(invoice.items) { idx, li ->
            LineItemCard(
                li = li,
                onQtyChange = { v ->
                    update { items[idx] = items[idx].copy(qty = v) }
                },
                onUnitChange = { v ->
                    update { items[idx] = items[idx].copy(unit = v) }
                },
                onDescChange = { v ->
                    update { items[idx] = items[idx].copy(description = v) }
                },
                onRemove = {
                    update {
                        if (items.size > 1) items.removeAt(idx)
                    }
                }
            )
        }

        // Add item
        item {
            OutlinedButton(
                onClick = { update { items.add(LineItem()) } },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Add Line Item") }
        }

        // -------------------- SUMMARY --------------------
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {

                    Text("Summary", style = MaterialTheme.typography.titleMedium)

                    SummaryRow("Parts", parts.money())
                    SummaryRow("Labor", labor.money())
                    SummaryRow("Shop Fee", shopFee.money())
                    SummaryRow("Tax (${invoice.taxPercent.toPlainString()}%)", tax.money())

                    Divider()

                    SummaryRow("Grand Total", grand.money())
                }
            }
        }

        item { Spacer(Modifier.height(60.dp)) }
    }
}

// ---------------------- Reusable UI Pieces -------------------------

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium)
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label)
        Text(value)
    }
}

@Composable
private fun SimpleField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun LineItemCard(
    li: LineItem,
    onQtyChange: (BigDecimal) -> Unit,
    onUnitChange: (BigDecimal) -> Unit,
    onDescChange: (String) -> Unit,
    onRemove: () -> Unit
) {
    Card(
        Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            OutlinedTextField(
                value = li.description,
                onValueChange = onDescChange,
                label = { Text("Description") },
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                DecimalField("Qty", li.qty, onQtyChange)
                MoneyField("Unit", li.unit, onUnitChange)

                Text(
                    (li.qty * li.unit).setScale(2, RoundingMode.HALF_UP).money(),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge
                )
            }

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onRemove) { Text("Remove") }
            }
        }
    }
}

@Composable
private fun DecimalField(label: String, value: BigDecimal, onChange: (BigDecimal) -> Unit) {
    var t by remember(value) {
        mutableStateOf(value.stripTrailingZeros().toPlainString())
    }

    OutlinedTextField(
        value = t,
        onValueChange = {
            t = it
            it.toBigDecimalOrNull()?.let(onChange)
        },
        label = { Text(label) },
        modifier = Modifier.weight(1f)
    )
}

@Composable
private fun MoneyField(label: String, value: BigDecimal, onChange: (BigDecimal) -> Unit) {
    var t by remember(value) {
        mutableStateOf(value.setScale(2, RoundingMode.HALF_UP).toPlainString())
    }

    OutlinedTextField(
        value = t,
        onValueChange = {
            t = it
            it.toBigDecimalOrNull()?.setScale(2, RoundingMode.HALF_UP)?.let(onChange)
        },
        label = { Text(label) },
        modifier = Modifier.weight(1f)
    )
}

@Composable
private fun PercentField(label: String, value: BigDecimal, onChange: (BigDecimal) -> Unit) {
    var t by remember(value) {
        mutableStateOf(value.stripTrailingZeros().toPlainString())
    }

    OutlinedTextField(
        value = t,
        onValueChange = {
            t = it
            it.toBigDecimalOrNull()?.let(onChange)
        },
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth()
    )
}
