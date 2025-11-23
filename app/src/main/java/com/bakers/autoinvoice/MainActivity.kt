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
import androidx.compose.ui.graphics.Color
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
    var number: String = java.text.SimpleDateFormat("yyyyMMdd-HHmm", Locale.getDefault()).format(Date()),
    var customer: Customer = Customer(),
    var vehicle: Vehicle = Vehicle(),
    var laborHours: BigDecimal = BigDecimal.ZERO,
    var laborRate: BigDecimal = BigDecimal("150.00"),     // <- requested update
    var taxPercent: BigDecimal = BigDecimal("6.00"),      // <- MI tax
    var shopFee: BigDecimal = BigDecimal("5.00"),         // <- requested $5 fee
    var items: MutableList<LineItem> = mutableListOf(LineItem())
)

/* --------------------------- Helpers --------------------------- */

fun BigDecimal.money(): String = NumberFormat.getCurrencyInstance().format(this)

/* --------------------------- App Root --------------------------- */

@Composable
fun AutoInvoiceApp() {

    // Red / Maroon theme override
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(0xFF8B0000),     // deep red
            secondary = Color(0xFFB22222),   // firebrick
            background = Color(0xFF300000),  // dark maroon
            surface = Color(0xFF400000),     // card background
            onSurface = Color.White,
            onPrimary = Color.White
        )
    ) {

        var screen by remember { mutableStateOf(Screen.HOME) }
        var invoice by remember { mutableStateOf(Invoice()) }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            when (screen) {
                                Screen.HOME -> "AutoInvoice"
                                Screen.NEW -> "New Invoice"
                            }
                        )
                    }
                )
            }
        ) { inner ->
            Box(Modifier.padding(inner)) {
                when (screen) {
                    Screen.HOME -> HomeScreen(
                        onNewInvoice = {
                            invoice = Invoice()
                            screen = Screen.NEW
                        }
                    )
                    Screen.NEW -> NewInvoiceScreen(
                        invoice = invoice,
                        onUpdate = { invoice = it },
                        onBack = { screen = Screen.HOME }
                    )
                }
            }
        }
    }
}

private enum class Screen { HOME, NEW }

/* --------------------------- HOME SCREEN --------------------------- */

@Composable
fun HomeScreen(onNewInvoice: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Baker's Auto Repair", style = MaterialTheme.typography.headlineSmall, color = Color.White)
        Button(
            onClick = onNewInvoice,
            modifier = Modifier.fillMaxWidth()
        ) { Text("➕ New Invoice") }
    }
}

/* --------------------------- NEW INVOICE --------------------------- */

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

    /* ---------- Totals ---------- */
    val parts = remember(invoice) {
        invoice.items.fold(BigDecimal.ZERO) { acc, li ->
            acc + li.qty.multiply(li.unit).setScale(2, RoundingMode.HALF_UP)
        }
    }

    val labor = invoice.laborHours.multiply(invoice.laborRate).setScale(2, RoundingMode.HALF_UP)
    val taxable = parts.setScale(2, RoundingMode.HALF_UP) // TAX ONLY ON PARTS (required)
    val tax = taxable.multiply(invoice.taxPercent).divide(BigDecimal("100")).setScale(2, RoundingMode.HALF_UP)

    val grand = parts + labor + tax + invoice.shopFee

    /* ---------- UI ---------- */

    LazyColumn(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {

        /* Back button + Invoice # */
        item {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Invoice # ${invoice.number}", color = Color.White)
                OutlinedButton(onClick = onBack) {
                    Text("Back")
                }
            }
        }

        /* CUSTOMER */
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

        /* VEHICLE */
        item { SectionTitle("Vehicle") }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(invoice.vehicle.year, { update { vehicle = vehicle.copy(year = it) } }, label = { Text("Year") })
                OutlinedTextField(invoice.vehicle.make, { update { vehicle = vehicle.copy(make = it) } }, label = { Text("Make") })
                OutlinedTextField(invoice.vehicle.model, { update { vehicle = vehicle.copy(model = it) } }, label = { Text("Model") })
                OutlinedTextField(invoice.vehicle.vin, { update { vehicle = vehicle.copy(vin = it.uppercase(Locale.US)) } }, label = { Text("VIN") })
            }
        }

        /* LABOR */
        item { SectionTitle("Labor") }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MoneyField("Rate/hr", invoice.laborRate) { v -> update { laborRate = v } }
                DecimalField("Hours", invoice.laborHours) { v -> update { laborHours = v } }
            }
        }

        /* ITEMS */
        item { SectionTitle("Parts / Services") }
        itemsIndexed(invoice.items) { idx, li ->
            Card(Modifier.fillMaxWidth()) {
                Column(
                    Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        li.description,
                        { v -> update { items[idx] = items[idx].copy(description = v) } },
                        label = { Text("Description") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        DecimalField("Qty", li.qty) { v -> update { items[idx] = items[idx].copy(qty = v) } }
                        MoneyField("Unit", li.unit) { v -> update { items[idx] = items[idx].copy(unit = v) } }
                        Text("Line: ${li.qty.multiply(li.unit).setScale(2, RoundingMode.HALF_UP).money()}")
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { update { if (items.size > 1) items.removeAt(idx) } }) {
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
            ) { Text("Add Line Item") }
        }

        /* TOTALS */
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(
                    Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Summary", color = Color.White)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Parts"); Text(parts.money())
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Labor"); Text(labor.money())
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Tax (Parts Only)"); Text(tax.money())
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Shop Fee"); Text(invoice.shopFee.money())
                    }
                    Divider()
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Grand Total"); Text(grand.money())
                    }
                }
            }
        }

        item { Spacer(Modifier.height(40.dp)) }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, color = Color.White, style = MaterialTheme.typography.titleMedium)
}

@Composable
private fun DecimalField(label: String, value: BigDecimal, onChange: (BigDecimal) -> Unit) {
    var t by remember(value) { mutableStateOf(value.stripTrailingZeros().toPlainString()) }
    OutlinedTextField(
        value = t,
        onValueChange = { t = it; it.toBigDecimalOrNull()?.let(onChange) },
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun MoneyField(label: String, value: BigDecimal, onChange: (BigDecimal) -> Unit) {
    var t by remember(value) { mutableStateOf(value.setScale(2, RoundingMode.HALF_UP).toPlainString()) }
    OutlinedTextField(
        value = t,
        onValueChange = { t = it; it.toBigDecimalOrNull()?.setScale(2, RoundingMode.HALF_UP)?.let(onChange) },
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth()
    )
}
