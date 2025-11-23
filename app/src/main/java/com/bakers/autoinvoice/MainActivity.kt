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

// ---------------------------------------------------------
// DATA MODELS
// ---------------------------------------------------------
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
        "yyyyMMdd-HHmm", Locale.getDefault()
    ).format(java.util.Date()),
    var customer: Customer = Customer(),
    var vehicle: Vehicle = Vehicle(),
    var laborHours: BigDecimal = BigDecimal.ZERO,
    var laborRate: BigDecimal = BigDecimal("150.00"),   // FIXED RATE
    var taxPercentPartsOnly: BigDecimal = BigDecimal("6.00"), // FIXED 6%
    val shopFee: BigDecimal = BigDecimal("5.00"),       // FIXED SHOP FEE
    var items: MutableList<LineItem> = mutableListOf(LineItem())
)

// ---------------------------------------------------------
// MAIN ACTIVITY
// ---------------------------------------------------------
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AutoInvoiceApp() }
    }
}

private enum class Screen { HOME, NEW }

// ---------------------------------------------------------
// APP ROOT
// ---------------------------------------------------------
@Composable
fun AutoInvoiceApp() {
    var screen by remember { mutableStateOf(Screen.HOME) }
    var invoice by remember { mutableStateOf(Invoice()) }

    MaterialTheme {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(if (screen == Screen.HOME) "AutoInvoice" else "New Invoice")
                    }
                )
            }
        ) { innerPadding ->
            Box(Modifier.padding(innerPadding)) {
                when (screen) {
                    Screen.HOME ->
                        HomeScreen(
                            onNew = {
                                invoice = Invoice()
                                screen = Screen.NEW
                            }
                        )

                    Screen.NEW ->
                        NewInvoiceScreen(
                            invoice = invoice,
                            onUpdate = { invoice = it },
                            onBack = { screen = Screen.HOME }
                        )
                }
            }
        }
    }
}

// ---------------------------------------------------------
// HOME SCREEN
// ---------------------------------------------------------
@Composable
private fun HomeScreen(onNew: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Baker's Auto — Invoice App", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(24.dp))
        Button(onClick = onNew, modifier = Modifier.fillMaxWidth()) {
            Text("➕ New Invoice")
        }
    }
}

// ---------------------------------------------------------
// NEW INVOICE SCREEN
// ---------------------------------------------------------
@Composable
private fun NewInvoiceScreen(
    invoice: Invoice,
    onUpdate: (Invoice) -> Unit,
    onBack: () -> Unit
) {
    fun modify(block: Invoice.() -> Unit) {
        val copy = invoice.copy(
            customer = invoice.customer.copy(),
            vehicle = invoice.vehicle.copy(),
            items = invoice.items.map { it.copy() }.toMutableList()
        )
        copy.block()
        onUpdate(copy)
    }

    fun BigDecimal.money(): String =
        NumberFormat.getCurrencyInstance().format(this)

    // VALUES
    val partsTotal = remember(invoice.items) {
        invoice.items.fold(BigDecimal.ZERO) { total, item ->
            total + (item.qty * item.unit).setScale(2, RoundingMode.HALF_UP)
        }
    }

    val laborTotal = remember(invoice.laborHours) {
        (invoice.laborHours * invoice.laborRate).setScale(2, RoundingMode.HALF_UP)
    }

    // TAX ONLY ON PARTS
    val taxAmount = remember(partsTotal) {
        partsTotal
            .multiply(invoice.taxPercentPartsOnly)
            .divide(BigDecimal("100"))
            .setScale(2, RoundingMode.HALF_UP)
    }

    val grandTotal = partsTotal + laborTotal + taxAmount + invoice.shopFee

    LazyColumn(
        Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {

        // Header
        item {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Invoice # ${invoice.number}", style = MaterialTheme.typography.titleMedium)
                OutlinedButton(onClick = onBack) { Text("Back") }
            }
        }

        // CUSTOMER SECTION
        item { Text("Customer", style = MaterialTheme.typography.titleMedium) }
        item {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = invoice.customer.name,
                    onValueChange = { modify { customer = customer.copy(name = it) } },
                    label = { Text("Name") }
                )
                OutlinedTextField(
                    value = invoice.customer.phone,
                    onValueChange = { modify { customer = customer.copy(phone = it) } },
                    label = { Text("Phone") }
                )
                OutlinedTextField(
                    value = invoice.customer.email,
                    onValueChange = { modify { customer = customer.copy(email = it) } },
                    label = { Text("Email") }
                )
            }
        }

        // VEHICLE SECTION
        item { Text("Vehicle", style = MaterialTheme.typography.titleMedium) }
        item {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = invoice.vehicle.year,
                    onValueChange = { modify { vehicle = vehicle.copy(year = it) } },
                    label = { Text("Year") }
                )
                OutlinedTextField(
                    value = invoice.vehicle.make,
                    onValueChange = { modify { vehicle = vehicle.copy(make = it) } },
                    label = { Text("Make") }
                )
                OutlinedTextField(
                    value = invoice.vehicle.model,
                    onValueChange = { modify { vehicle = vehicle.copy(model = it) } },
                    label = { Text("Model") }
                )
                OutlinedTextField(
                    value = invoice.vehicle.vin,
                    onValueChange = { modify { vehicle = vehicle.copy(vin = it.uppercase()) } },
                    label = { Text("VIN") }
                )
            }
        }

        // LABOR SECTION
        item { Text("Labor", style = MaterialTheme.typography.titleMedium) }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = invoice.laborRate.toPlainString(),
                    onValueChange = {},
                    enabled = false,
                    label = { Text("Rate/hr (Fixed)") }
                )
                DecimalField("Hours", invoice.laborHours) {
                    modify { laborHours = it }
                }
            }
        }

        // PARTS / SERVICES
        item { Text("Parts / Services", style = MaterialTheme.typography.titleMedium) }

        itemsIndexed(invoice.items) { index, item ->
            Card(Modifier.fillMaxWidth()) {
                Column(
                    Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = item.description,
                        onValueChange = { txt ->
                            modify { items[index] = items[index].copy(description = txt) }
                        },
                        label = { Text("Description") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        DecimalField("Qty", item.qty) { qty ->
                            modify { items[index] = items[index].copy(qty = qty) }
                        }

                        MoneyField("Unit", item.unit) { unit ->
                            modify { items[index] = items[index].copy(unit = unit) }
                        }

                        Text(
                            "Line: ${(item.qty * item.unit).setScale(2, RoundingMode.HALF_UP).money()}",
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(
                            onClick = {
                                modify {
                                    if (items.size > 1) items.removeAt(index)
                                }
                            }
                        ) { Text("Remove") }
                    }
                }
            }
        }

        // ADD ITEM BUTTON
        item {
            OutlinedButton(
                onClick = { modify { items.add(LineItem()) } },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Add Line Item") }
        }

        // SUMMARY CARD
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(
                    Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Summary", style = MaterialTheme.typography.titleMedium)

                    RowSummary("Parts", partsTotal.money())
                    RowSummary("Labor", laborTotal.money())
                    RowSummary("Tax (6% on Parts)", taxAmount.money())
                    RowSummary("Shop Fee", invoice.shopFee.money())

                    Divider()

                    RowSummary("Grand Total", grandTotal.money())
                }
            }
        }

        item { Spacer(Modifier.height(48.dp)) }
    }
}

// ---------------------------------------------------------
// REUSABLE ROW FOR SUMMARY
// ---------------------------------------------------------
@Composable
private fun RowSummary(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label)
        Text(value)
    }
}

// ---------------------------------------------------------
// FIELD COMPOSABLES
// ---------------------------------------------------------
@Composable
private fun DecimalField(
    label: String,
    value: BigDecimal,
    onChange: (BigDecimal) -> Unit
) {
    var text by remember(value) {
        mutableStateOf(value.stripTrailingZeros().toPlainString())
    }

    OutlinedTextField(
        value = text,
        onValueChange = {
            text = it
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
    var text by remember(value) {
        mutableStateOf(value.setScale(2, RoundingMode.HALF_UP).toPlainString())
    }

    OutlinedTextField(
        value = text,
        onValueChange = {
            text = it
            it.toBigDecimalOrNull()?.setScale(2, RoundingMode.HALF_UP)?.let(onChange)
        },
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth()
    )
}
