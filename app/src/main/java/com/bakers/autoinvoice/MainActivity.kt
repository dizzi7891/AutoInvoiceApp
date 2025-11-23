package com.bakers.autoinvoice

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
    // simple date/time-based invoice number
    var number: String = java.text.SimpleDateFormat(
        "yyyyMMdd-HHmm",
        Locale.getDefault()
    ).format(java.util.Date()),
    var customer: Customer = Customer(),
    var vehicle: Vehicle = Vehicle(),
    var laborHours: BigDecimal = BigDecimal.ZERO,
    // YOUR SETTINGS: $150 / hour labor
    var laborRate: BigDecimal = BigDecimal("150.00"),
    // YOUR SETTINGS: 6% sales tax on PARTS ONLY
    var taxPercent: BigDecimal = BigDecimal("6.00"),
    // YOUR SETTINGS: $5 flat shop fee on every invoice
    var shopFee: BigDecimal = BigDecimal("5.00"),
    var items: MutableList<LineItem> = mutableListOf(LineItem())
)

// Money formatting helper
private fun BigDecimal.money(): String =
    NumberFormat.getCurrencyInstance().format(this)

// ----------------------------- UI Root ----------------------------

@OptIn(ExperimentalMaterial3Api::class) // <-- fixes the "experimental material API" error
@Composable
fun AutoInvoiceApp() {
    var screen by remember { mutableStateOf(Screen.HOME) }
    var inv by remember { mutableStateOf(Invoice()) }

    MaterialTheme {
        Scaffold(
            topBar = {
                // Simple manual top bar, no fancy experimental APIs
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(
                        text = if (screen == Screen.HOME) "AutoInvoice" else "New Invoice",
                        style = MaterialTheme.typography.titleLarge
                    )
                }
            }
        ) { inner ->
            Box(Modifier.padding(inner)) {
                when (screen) {
                    Screen.HOME -> HomeScreen(
                        onNew = {
                            inv = Invoice() // reset invoice when starting a new one
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
        Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "Baker's Auto — Invoice App",
            style = MaterialTheme.typography.headlineSmall
        )
        Button(
            onClick = onNew,
            modifier = Modifier.fillMaxWidth()
        ) {
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
    // Helper to make safe, copy-based updates
    fun update(block: Invoice.() -> Unit) {
        val copy = invoice.copy(
            customer = invoice.customer.copy(),
            vehicle = invoice.vehicle.copy(),
            items = invoice.items.map { it.copy() }.toMutableList()
        )
        copy.block()
        onUpdate(copy)
    }

    // ---- Calculations (using your business rules) ----

    // Parts subtotal
    val parts = remember(invoice) {
        invoice.items.fold(BigDecimal.ZERO) { acc, li ->
            acc + li.qty.multiply(li.unit).setScale(2, RoundingMode.HALF_UP)
        }
    }

    // Labor total (hours * $150)
    val labor = remember(invoice) {
        invoice.laborHours.multiply(invoice.laborRate).setScale(2, RoundingMode.HALF_UP)
    }

    // Shop fee ($5 flat)
    val shopFee = invoice.shopFee.setScale(2, RoundingMode.HALF_UP)

    // Taxable base = PARTS ONLY
    val taxableParts = remember(parts) {
        parts.setScale(2, RoundingMode.HALF_UP)
    }

    // 6% tax on parts only
    val tax = remember(invoice, taxableParts) {
        taxableParts.multiply(invoice.taxPercent)
            .divide(BigDecimal("100"))
            .setScale(2, RoundingMode.HALF_UP)
    }

    // Grand total = parts + labor + shop fee + tax (on parts)
    val grand = remember(parts, labor, shopFee, tax) {
        (parts + labor + shopFee + tax).setScale(2, RoundingMode.HALF_UP)
    }

    // ---- Screen layout ----

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header row
        item {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Invoice # ${invoice.number}",
                    style = MaterialTheme.typography.titleMedium
                )
                OutlinedButton(onClick = onBack) {
                    Text("Back")
                }
            }
        }

        // ---------- CUSTOMER ----------
        item { SectionTitle("Customer") }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = invoice.customer.email,
                    onValueChange = { update { customer = customer.copy(email = it) } },
                    label = { Text("Email") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // ---------- VEHICLE ----------
        item { SectionTitle("Vehicle") }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = invoice.vehicle.year,
                    onValueChange = { update { vehicle = vehicle.copy(year = it) } },
                    label = { Text("Year") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = invoice.vehicle.make,
                    onValueChange = { update { vehicle = vehicle.copy(make = it) } },
                    label = { Text("Make") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = invoice.vehicle.model,
                    onValueChange = { update { vehicle = vehicle.copy(model = it) } },
                    label = { Text("Model") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = invoice.vehicle.vin,
                    onValueChange = {
                        update {
                            vehicle = vehicle.copy(vin = it.uppercase(Locale.US))
                        }
                    },
                    label = { Text("VIN") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // ---------- LABOR ----------
        item { SectionTitle("Labor") }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MoneyField(
                    label = "Rate/hr",
                    value = invoice.laborRate
                ) { v -> update { laborRate = v } }

                DecimalField(
                    label = "Hours",
                    value = invoice.laborHours
                ) { v -> update { laborHours = v } }

                PercentField(
                    label = "Tax % (parts only)",
                    value = invoice.taxPercent
                ) { v -> update { taxPercent = v } }
            }
        }

        // ---------- PARTS / SERVICES ----------
        item { SectionTitle("Parts / Services") }
        itemsIndexed(invoice.items) { idx, li ->
            Card(Modifier.fillMaxWidth()) {
                Column(
                    Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = li.description,
                        onValueChange = { v ->
                            update {
                                items[idx] = items[idx].copy(description = v)
                            }
                        },
                        label = { Text("Description") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        DecimalField(
                            label = "Qty",
                            value = li.qty
                        ) { v ->
                            update {
                                items[idx] = items[idx].copy(qty = v)
                            }
                        }
                        MoneyField(
                            label = "Unit",
                            value = li.unit
                        ) { v ->
                            update {
                                items[idx] = items[idx].copy(unit = v)
                            }
                        }
                        Text(
                            "Line: ${
                                li.qty.multiply(li.unit)
                                    .setScale(2, RoundingMode.HALF_UP)
                                    .money()
                            }"
                        )
                    }

                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(
                            onClick = {
                                update {
                                    if (items.size > 1) items.removeAt(idx)
                                }
                            }
                        ) {
                            Text("Remove")
                        }
                    }
                }
            }
        }

        item {
            OutlinedButton(
                onClick = { update { items.add(LineItem()) } },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Add Line Item")
            }
        }

        // ---------- SUMMARY ----------
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(
                    Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Summary", style = MaterialTheme.typography.titleMedium)

                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Parts")
                        Text(parts.money())
                    }

                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Labor")
                        Text(labor.money())
                    }

                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Shop Fee")
                        Text(shopFee.money())
                    }

                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Tax on Parts (${invoice.taxPercent.stripTrailingZeros().toPlainString()}%)")
                        Text(tax.money())
                    }

                    Divider()

                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Grand Total")
                        Text(grand.money())
                    }
                }
            }
        }

        item { Spacer(Modifier.height(48.dp)) }
    }
}

// ----------------------- Reusable Fields --------------------------

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium
    )
}

@Composable
private fun DecimalField(
    label: String,
    value: BigDecimal,
    onChange: (BigDecimal) -> Unit
) {
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

@Composable
private fun MoneyField(
    label: String,
    value: BigDecimal,
    onChange: (BigDecimal) -> Unit
) {
    var t by remember(value) {
        mutableStateOf(
            value.setScale(2, RoundingMode.HALF_UP).toPlainString()
        )
    }

    OutlinedTextField(
        value = t,
        onValueChange = {
            t = it
            it.toBigDecimalOrNull()
                ?.setScale(2, RoundingMode.HALF_UP)
                ?.let(onChange)
        },
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun PercentField(
    label: String,
    value: BigDecimal,
    onChange: (BigDecimal) -> Unit
) {
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
