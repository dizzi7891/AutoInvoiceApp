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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bakers.autoinvoice.ui.theme.AutoInvoiceTheme
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Locale

/* ---------------------------------------------------------------
   MODELS
--------------------------------------------------------------- */
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
    var number: String = java.text.SimpleDateFormat("yyyyMMdd-HHmm", Locale.getDefault()).format(java.util.Date()),
    var customer: Customer = Customer(),
    var vehicle: Vehicle = Vehicle(),
    var laborHours: BigDecimal = BigDecimal.ZERO,
    var laborRate: BigDecimal = BigDecimal("150.00"),   // your default labor rate
    var taxPercent: BigDecimal = BigDecimal("6.00"),    // Michigan parts tax
    var items: MutableList<LineItem> = mutableListOf(LineItem())
)

/* ---------------------------------------------------------------
   MAIN ACTIVITY
--------------------------------------------------------------- */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AutoInvoiceTheme {
                AutoInvoiceApp()
            }
        }
    }
}

/* ---------------------------------------------------------------
   NAVIGATION ENUM
--------------------------------------------------------------- */
private enum class Screen { HOME, NEW }

/* ---------------------------------------------------------------
   ROOT APP COMPOSABLE
--------------------------------------------------------------- */
@Composable
fun AutoInvoiceApp() {
    var screen by remember { mutableStateOf(Screen.HOME) }
    var inv by remember { mutableStateOf(Invoice()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (screen == Screen.HOME) "AutoInvoice"
                        else "New Invoice"
                    )
                }
            )
        }
    ) { inner ->
        Box(Modifier.padding(inner)) {
            when (screen) {
                Screen.HOME ->
                    HomeScreen(
                        onNew = { inv = Invoice(); screen = Screen.NEW }
                    )
                Screen.NEW ->
                    NewInvoiceScreen(
                        invoice = inv,
                        onUpdate = { inv = it },
                        onBack = { screen = Screen.HOME }
                    )
            }
        }
    }
}

/* ---------------------------------------------------------------
   HOME SCREEN
--------------------------------------------------------------- */
@Composable
fun HomeScreen(onNew: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Baker's Auto — Invoice App",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(32.dp))

        Button(
            onClick = onNew,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("➕ Create New Invoice")
        }
    }
}

/* ---------------------------------------------------------------
   INVOICE SCREEN
--------------------------------------------------------------- */
@Composable
fun NewInvoiceScreen(
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

    fun BigDecimal.money(): String =
        NumberFormat.getCurrencyInstance().format(this)

    val shopFee = BigDecimal("5.00")   // mandatory MI-compliant shop fee

    val parts = remember(invoice) {
        invoice.items.fold(BigDecimal.ZERO) { a, li ->
            a + li.qty.multiply(li.unit).setScale(2, RoundingMode.HALF_UP)
        }
    }
    val labor = remember(invoice) {
        invoice.laborHours.multiply(invoice.laborRate)
            .setScale(2, RoundingMode.HALF_UP)
    }
    val taxable = parts
    val tax = taxable.multiply(invoice.taxPercent)
        .divide(BigDecimal("100"))
        .setScale(2, RoundingMode.HALF_UP)
    val grand = parts + labor + tax + shopFee

    LazyColumn(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Invoice # ${invoice.number}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                OutlinedButton(onClick = onBack) { Text("Back") }
            }
        }

        /* CUSTOMER */
        item { Section("Customer") }
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

        /* VEHICLE */
        item { Section("Vehicle") }
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
                    update { vehicle = vehicle.copy(vin = it.uppercase(Locale.US)) }
                }
            }
        }

        /* LABOR */
        item { Section("Labor") }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MoneyField("Labor Rate/hr", invoice.laborRate) {
                    v -> update { laborRate = v }
                }
                DecimalField("Hours", invoice.laborHours) {
                    v -> update { laborHours = v }
                }
                PercentField("Tax % (Parts Only)", invoice.taxPercent) {
                    v -> update { taxPercent = v }
                }
            }
        }

        /* PARTS & SERVICES */
        item { Section("Parts & Services") }
        itemsIndexed(invoice.items) { idx, li ->
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SimpleField("Description", li.description) {
                        v -> update { items[idx] = items[idx].copy(description = v) }
                    }

                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        DecimalField("Qty", li.qty) {
                            v -> update { items[idx] = items[idx].copy(qty = v) }
                        }
                        MoneyField("Unit Price", li.unit) {
                            v -> update { items[idx] = items[idx].copy(unit = v) }
                        }
                    }

                    Text(
                        "Line Total: " +
                                li.qty.multiply(li.unit)
                                    .setScale(2, RoundingMode.HALF_UP)
                                    .money(),
                        fontWeight = FontWeight.SemiBold
                    )

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
                        ) { Text("Remove") }
                    }
                }
            }
        }

        item {
            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    update { items.add(LineItem()) }
                }
            ) {
                Text("Add Line Item")
            }
        }

        /* SUMMARY */
        item { Section("Totals") }
        item {
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SummaryRow("Parts", parts.money())
                    SummaryRow("Labor", labor.money())
                    SummaryRow("Tax (${invoice.taxPercent.stripTrailingZeros()}%)", tax.money())
                    SummaryRow("Shop Fee", shopFee.money())
                    Divider()
                    SummaryRow("Grand Total", grand.money(), bold = true)
                }
            }
        }

        item { Spacer(Modifier.height(60.dp)) }
    }
}

/* ---------------------------------------------------------------
   REUSABLE UI ELEMENTS
--------------------------------------------------------------- */
@Composable
private fun Section(title: String) {
    Text(title,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold
    )
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
        modifier = Modifier.fillMaxWidth()
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
            it.toBigDecimalOrNull()
                ?.setScale(2, RoundingMode.HALF_UP)
                ?.let(onChange)
        },
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth()
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

@Composable
private fun SummaryRow(label: String, value: String, bold: Boolean = false) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label)
        Text(
            value,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal
        )
    }
}
