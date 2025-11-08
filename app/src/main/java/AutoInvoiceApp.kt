package com.bakers.autoinvoice

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.WorkerThread
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.room.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.OutputStream
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

// ===== Data Models =====

data class ShopSettings(
    var shopName: String = "Baker's Automotive Repair, LLC",
    var address: String = "33860 Groesbeck Hwy, Clinton Township, MI 48035",
    var phone: String = "(586) 843-4157",
    var email: String = "service@bakersautorepair.com",
    var laborRatePerHour: BigDecimal = BigDecimal("110.00"),
    var taxRatePercent: BigDecimal = BigDecimal("6.00"),
    var warrantyText: String = "12-month / 12,000-mile parts & labor warranty on qualifying repairs.",
    var logoUri: String? = null
)

data class Customer(var name: String = "", var phone: String = "", var email: String = "", var address: String = "")
data class Vehicle(var year: String = "", var make: String = "", var model: String = "", var vin: String = "")
data class LineItem(var description: String = "", var qty: BigDecimal = BigDecimal.ONE, var unitPrice: BigDecimal = BigDecimal.ZERO) {
    fun lineTotal(): BigDecimal = qty.multiply(unitPrice).setScale(2, RoundingMode.HALF_UP)
}
data class Invoice(
    var invoiceNumber: String = SimpleDateFormat("yyyyMMdd-HHmm", Locale.getDefault()).format(Date()),
    var date: Date = Date(),
    var customer: Customer = Customer(),
    var vehicle: Vehicle = Vehicle(),
    var laborHours: BigDecimal = BigDecimal.ZERO,
    var notes: String = "",
    var lineItems: MutableList<LineItem> = mutableListOf()
)

// ===== Math Helpers =====
private fun BigDecimal.prettyMoney(): String = NumberFormat.getCurrencyInstance().format(this)
private operator fun BigDecimal.plus(other: BigDecimal) = this.add(other)

private data class Totals(val parts: BigDecimal, val labor: BigDecimal, val taxable: BigDecimal, val tax: BigDecimal, val grand: BigDecimal)
private fun computeTotals(invoice: Invoice, settings: ShopSettings): Totals {
    val parts = invoice.lineItems.fold(BigDecimal.ZERO) { acc, li -> acc + li.lineTotal() }.setScale(2, RoundingMode.HALF_UP)
    val labor = invoice.laborHours.multiply(settings.laborRatePerHour).setScale(2, RoundingMode.HALF_UP)
    val taxable = (parts + labor).setScale(2, RoundingMode.HALF_UP)
    val tax = taxable.multiply(settings.taxRatePercent).divide(BigDecimal("100"), 2, RoundingMode.HALF_UP)
    val grand = (taxable + tax).setScale(2, RoundingMode.HALF_UP)
    return Totals(parts, labor, taxable, tax, grand)
}

// ===== Main UI =====
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AutoInvoiceApp() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutoInvoiceApp() {
    var settings by remember { mutableStateOf(ShopSettings()) }
    var invoice by remember { mutableStateOf(Invoice()) }
    var selectedTab by remember { mutableStateOf(0) }
    val totals = remember(invoice, settings) { computeTotals(invoice, settings) }

    MaterialTheme {
        Scaffold(
            topBar = { TopAppBar(title = { Text("AutoInvoice") }) }
        ) { inner ->
            Column(Modifier.padding(inner)) {
                TabRow(selectedTabIndex = selectedTab) {
                    Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Invoice") })
                    Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Settings") })
                }
                when (selectedTab) {
                    0 -> InvoiceScreen(invoice, { invoice = it }, settings)
                    1 -> SettingsScreen(settings, { settings = it })
                }
            }
        }
    }
}

@Composable
fun InvoiceScreen(invoice: Invoice, onChange: (Invoice) -> Unit, settings: ShopSettings) {
    val i = invoice
    val totals = computeTotals(i, settings)
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Customer", fontWeight = FontWeight.Bold) }
        item {
            OutlinedTextField(value = i.customer.name, onValueChange = { onChange(i.copy(customer = i.customer.copy(name = it))) }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
        }
        item { Text("Vehicle", fontWeight = FontWeight.Bold) }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = i.vehicle.year, onValueChange = { onChange(i.copy(vehicle = i.vehicle.copy(year = it))) }, label = { Text("Year") }, modifier = Modifier.weight(1f))
                OutlinedTextField(value = i.vehicle.make, onValueChange = { onChange(i.copy(vehicle = i.vehicle.copy(make = it))) }, label = { Text("Make") }, modifier = Modifier.weight(1f))
                OutlinedTextField(value = i.vehicle.model, onValueChange = { onChange(i.copy(vehicle = i.vehicle.copy(model = it))) }, label = { Text("Model") }, modifier = Modifier.weight(1f))
            }
        }
        item { Text("Labor Hours: ${i.laborHours}", fontWeight = FontWeight.Bold) }
        item { Text("Parts / Services", fontWeight = FontWeight.Bold) }
        items(i.lineItems) { li ->
            Column {
                OutlinedTextField(value = li.description, onValueChange = {
                    val list = i.lineItems.toMutableList()
                    val idx = list.indexOf(li)
                    if (idx >= 0) list[idx] = li.copy(description = it)
                    onChange(i.copy(lineItems = list))
                }, label = { Text("Description") }, modifier = Modifier.fillMaxWidth())
            }
        }
        item { Button(onClick = { val list = i.lineItems.toMutableList(); list.add(LineItem()); onChange(i.copy(lineItems = list)) }) { Text("Add Line Item") } }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Parts: ${totals.parts.prettyMoney()}")
                    Text("Labor: ${totals.labor.prettyMoney()}")
                    Text("Tax: ${totals.tax.prettyMoney()}")
                    Divider()
                    Text("Total: ${totals.grand.prettyMoney()}", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(settings: ShopSettings, onChange: (ShopSettings) -> Unit) {
    val s = settings
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Shop Settings", fontWeight = FontWeight.Bold) }
        item { OutlinedTextField(value = s.shopName, onValueChange = { onChange(s.copy(shopName = it)) }, label = { Text("Shop Name") }, modifier = Modifier.fillMaxWidth()) }
        item { OutlinedTextField(value = s.address, onValueChange = { onChange(s.copy(address = it)) }, label = { Text("Address") }, modifier = Modifier.fillMaxWidth()) }
        item { OutlinedTextField(value = s.phone, onValueChange = { onChange(s.copy(phone = it)) }, label = { Text("Phone") }, modifier = Modifier.fillMaxWidth()) }
        item { OutlinedTextField(value = s.email, onValueChange = { onChange(s.copy(email = it)) }, label = { Text("Email") }, modifier = Modifier.fillMaxWidth()) }
        item { OutlinedTextField(value = s.warrantyText, onValueChange = { onChange(s.copy(warrantyText = it)) }, label = { Text("Warranty Text") }, modifier = Modifier.fillMaxWidth()) }
    }
}
