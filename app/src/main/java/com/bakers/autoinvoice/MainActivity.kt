package com.bakers.autoinvoice

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
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

private enum class Screen { HOME, NEW }

data class Customer(var name: String = "", var phone: String = "", var email: String = "")
data class Vehicle(var year: String = "", var make: String = "", var model: String = "", var vin: String = "")
data class LineItem(var description: String = "", var qty: BigDecimal = BigDecimal.ONE, var unit: BigDecimal = BigDecimal.ZERO)

data class Invoice(
    var number: String = SimpleDateFormat("yyyyMMdd-HHmm", Locale.getDefault()).format(Date()),
    var customer: Customer = Customer(),
    var vehicle: Vehicle = Vehicle(),
    var laborHours: BigDecimal = BigDecimal.ZERO,
    var laborRate: BigDecimal = BigDecimal("150.00"),
    var taxPercent: BigDecimal = BigDecimal("6.00"),
    var shopFee: BigDecimal = BigDecimal("5.00"),
    var items: MutableList<LineItem> = mutableListOf(LineItem())
)

@Composable
fun AutoInvoiceApp() {
    var screen by remember { mutableStateOf(Screen.HOME) }
    var inv by remember { mutableStateOf(Invoice()) }

    MaterialTheme {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(if (screen == Screen.HOME) "AutoInvoice" else "New Invoice") }
                )
            }
        ) { inner ->
            Box(
                Modifier
                    .padding(inner)
                    .fillMaxSize()
                    .background(Color(0xFF800000)) // MAROON BACKGROUND
            ) {
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
        Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Baker's Auto — Invoice App", color = Color.White, style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(20.dp))
        Button(onClick = onNew) { Text("➕ New Invoice") }
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
        copy.block()
        onUpdate(copy)
    }

    fun BigDecimal.money(): String = NumberFormat.getCurrencyInstance().format(this)

    // TAX = 6% of PARTS only
    val parts = invoice.items.fold(BigDecimal.ZERO) { a, li ->
        a + li.qty.multiply(li.unit).setScale(2, RoundingMode.HALF_UP)
    }

    val labor = invoice.laborHours.multiply(invoice.laborRate).setScale(2, RoundingMode.HALF_UP)
    val tax = parts.multiply(invoice.taxPercent).divide(BigDecimal("100")).setScale(2, RoundingMode.HALF_UP)
    val grand = parts + labor + tax + invoice.shopFee

    LazyColumn(
        Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {

        // Header bar
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Invoice # ${invoice.number}", color = Color.White)
                OutlinedButton(onClick = onBack) { Text("Back") }
            }
        }

        // Customer fields
        item { Text("Customer", color = Color.White) }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(invoice.customer.name, { update { customer = customer.copy(name = it) } }, label = { Text("Name") })
                OutlinedTextField(invoice.customer.phone, { update { customer = customer.copy(phone = it) } }, label = { Text("Phone") })
                OutlinedTextField(invoice.customer.email, { update { customer = customer.copy(email = it) } }, label = { Text("Email") })
            }
        }

        // Vehicle
        item { Text("Vehicle", color = Color.White) }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(invoice.vehicle.year, { update { vehicle = vehicle.copy(year = it) } }, label = { Text("Year") })
                OutlinedTextField(invoice.vehicle.make, { update { vehicle = vehicle.copy(make = it) } }, label = { Text("Make") })
                OutlinedTextField(invoice.vehicle.model, { update { vehicle = vehicle.copy(model = it) } }, label = { Text("Model") })
                OutlinedTextField(invoice.vehicle.vin, { update { vehicle = vehicle.copy(vin = it.uppercase()) } }, label = { Text("VIN") })
            }
        }

        // Labor
        item { Text("Labor", color = Color.White) }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DecimalField("Hours", invoice.laborHours) { v -> update { laborHours = v } }
            }
        }

        // Parts
        item { Text("Parts / Services", color = Color.White) }
        itemsIndexed(invoice.items) { idx, li ->
            Card {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(li.description, { update { items[idx] = items[idx].copy(description = it) } }, label = { Text("Description") })
                    OutlinedTextField(li.qty.toPlainString(), {
                        it.toBigDecimalOrNull()?.let { bd -> update { items[idx] = items[idx].copy(qty = bd) } }
                    }, label = { Text("Qty") })
                    OutlinedTextField(li.unit.toPlainString(), {
                        it.toBigDecimalOrNull()?.let { bd -> update { items[idx] = items[idx].copy(unit = bd) } }
                    }, label = { Text("Unit Price") })
                }
            }
        }
        item { OutlinedButton(onClick = { update { items.add(LineItem()) } }) { Text("Add Item") } }

        // Totals
        item {
            Card {
                Column(Modifier.padding(16.dp)) {
                    Text("Parts: ${parts.money()}")
                    Text("Labor: ${labor.money()}")
                    Text("Tax (6% parts): ${tax.money()}")
                    Text("Shop Fee: ${invoice.shopFee.money()}")
                    Spacer(Modifier.height(4.dp))
                    Text("Grand Total: ${grand.money()}")
                }
            }
        }
    }
}

@Composable
private fun DecimalField(label: String, value: BigDecimal, onChange: (BigDecimal) -> Unit) {
    var t by remember(value) { mutableStateOf(value.toPlainString()) }
    OutlinedTextField(t, { t = it; it.toBigDecimalOrNull()?.let(onChange) }, label = { Text(label) })
}
