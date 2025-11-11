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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AutoInvoiceApp() }
    }
}

private enum class Screen { HOME, NEW }

data class Customer(var name: String = "", var phone: String = "", var email: String = "")
data class Vehicle(var year: String = "", var make: String = "", var model: String = "", var vin: String = "")
data class LineItem(var description: String = "", var qty: BigDecimal = BigDecimal.ONE, var unit: BigDecimal = BigDecimal.ZERO)
data class Invoice(
    var number: String = java.text.SimpleDateFormat("yyyyMMdd-HHmm", Locale.getDefault()).format(java.util.Date()),
    var customer: Customer = Customer(),
    var vehicle: Vehicle = Vehicle(),
    var laborHours: BigDecimal = BigDecimal.ZERO,
    var laborRate: BigDecimal = BigDecimal("110.00"),
    var taxPercent: BigDecimal = BigDecimal("6.00"),
    var items: MutableList<LineItem> = mutableListOf(LineItem())
)

@Composable
fun AutoInvoiceApp() {
    var screen by remember { mutableStateOf(Screen.HOME) }
    var inv by remember { mutableStateOf(Invoice()) }

    MaterialTheme {
        Scaffold(
            topBar = { SmallTopAppBar(title = { Text(if (screen == Screen.HOME) "AutoInvoice" else "New Invoice") }) }
        ) { inner ->
            Box(Modifier.padding(inner)) {
                when (screen) {
                    Screen.HOME -> HomeScreen(onNew = { inv = Invoice(); screen = Screen.NEW })
                    Screen.NEW -> NewInvoiceScreen(invoice = inv, onUpdate = { inv = it }, onBack = { screen = Screen.HOME })
                }
            }
        }
    }
}

@Composable
private fun HomeScreen(onNew: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Baker's Auto — Invoice App", style = MaterialTheme.typography.headlineSmall)
        Button(onClick = onNew, modifier = Modifier.fillMaxWidth()) { Text("➕ New Invoice") }
    }
}

@Composable
private fun NewInvoiceScreen(invoice: Invoice, onUpdate: (Invoice) -> Unit, onBack: () -> Unit) {
    fun update(block: Invoice.() -> Unit) {
        val copy = invoice.copy(
            customer = invoice.customer.copy(),
            vehicle = invoice.vehicle.copy(),
            items = invoice.items.map { it.copy() }.toMutableList()
        )
        copy.block(); onUpdate(copy)
    }

    fun BigDecimal.money(): String = NumberFormat.getCurrencyInstance().format(this)

    val parts = remember(invoice) { invoice.items.fold(BigDecimal.ZERO) { a, li -> a + li.qty.multiply(li.unit).setScale(2, RoundingMode.HALF_UP) } }
    val labor = remember(invoice) { invoice.laborHours.multiply(invoice.laborRate).setScale(2, RoundingMode.HALF_UP) }
    val taxable = remember(parts, labor) { (parts + labor).setScale(2, RoundingMode.HALF_UP) }
    val tax = remember(invoice, taxable) { taxable.multiply(invoice.taxPercent).divide(BigDecimal("100")).setScale(2, RoundingMode.HALF_UP) }
    val grand = remember(taxable, tax) { (taxable + tax).setScale(2, RoundingMode.HALF_UP) }

    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Invoice # ${invoice.number}", style = MaterialTheme.typography.titleMedium)
                OutlinedButton(onClick = onBack) { Text("Back") }
            }
        }

        // Customer
        item { Text("Customer", style = MaterialTheme.typography.titleMedium) }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(invoice.customer.name, { update { customer = customer.copy(name = it) } }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(invoice.customer.phone, { update { customer = customer.copy(phone = it) } }, label = { Text("Phone") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(invoice.customer.email, { update { customer = customer.copy(email = it) } }, label = { Text("Email") }, modifier = Modifier.fillMaxWidth())
            }
        }

        // Vehicle
        item { Text("Vehicle", style = MaterialTheme.typography.titleMedium) }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(invoice.vehicle.year, { update { vehicle = vehicle.copy(year = it) } }, label = { Text("Year") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(invoice.vehicle.make, { update { vehicle = vehicle.copy(make = it) } }, label = { Text("Make") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(invoice.vehicle.model, { update { vehicle = vehicle.copy(model = it) } }, label = { Text("Model") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(invoice.vehicle.vin, { update { vehicle = vehicle.copy(vin = it.uppercase(Locale.US)) } }, label = { Text("VIN") }, modifier = Modifier.fillMaxWidth())
            }
        }

        // Labor / rates
        item { Text("Labor", style = MaterialTheme.typography.titleMedium) }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MoneyField("Rate/hr", invoice.laborRate) { v -> update { laborRate = v } }
                DecimalField("Hours", invoice.laborHours) { v -> update { laborHours = v } }
                PercentField("Tax %", invoice.taxPercent) { v -> update { taxPercent = v } }
            }
        }

        // Items
        item { Text("Parts / Services", style = MaterialTheme.typography.titleMedium) }
        itemsIndexed(invoice.items) { idx, li ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(li.description, { v -> update { items[idx] = items[idx].copy(description = v) } }, label = { Text("Description") }, modifier = Modifier.fillMaxWidth())
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        DecimalField("Qty", li.qty) { v -> update { items[idx] = items[idx].copy(qty = v) } }
                        MoneyField("Unit", li.unit) { v -> update { items[idx] = items[idx].copy(unit = v) } }
                        Text("Line: ${li.qty.multiply(li.unit).setScale(2, RoundingMode.HALF_UP).money()}")
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { update { if (items.size > 1) items.removeAt(idx) } }) { Text("Remove") }
                    }
                }
            }
        }
        item { OutlinedButton(onClick = { update { items.add(LineItem()) } }, modifier = Modifier.fillMaxWidth()) { Text("Add Line Item") } }

        // Totals
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Summary", style = MaterialTheme.typography.titleMedium)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Parts"); Text(parts.money()) }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Labor"); Text(labor.money()) }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Tax (${invoice.taxPercent.stripTrailingZeros().toPlainString()}%)"); Text(tax.money()) }
                    Divider()
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Grand Total"); Text(grand.money()) }
                }
            }
        }
        item { Spacer(Modifier.height(48.dp)) }
    }
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

@Composable
private fun PercentField(label: String, value: BigDecimal, onChange: (BigDecimal) -> Unit) {
    var t by remember(value) { mutableStateOf(value.stripTrailingZeros().toPlainString()) }
    OutlinedTextField(
        value = t,
        onValueChange = { t = it; it.toBigDecimalOrNull()?.let(onChange) },
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth()
    )
}
