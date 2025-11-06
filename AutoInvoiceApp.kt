// AutoInvoiceApp.kt — v7 (Free stack + ARI provider integrated)
// Requires: compileSdk 34+, Kotlin 1.9+, Compose BOM 2024.10+
// -------------------------------------------------------------
// IMPORTANT
// 1️⃣ Add <uses-permission android:name="android.permission.INTERNET"/> to AndroidManifest.xml
// 2️⃣ build.gradle must enable compose and include Room + Material3
// 3️⃣ Minimum SDK 24+

package com.bakers.autoinvoice

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.foundation.*
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
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.room.*
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.OutputStream
import java.math.BigDecimal
import java.math.RoundingMode
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

// ===================
// Shop + Settings
// ===================
data class ShopSettings(
    var shopName: String = "Baker's Automotive Repair, LLC",
    var address: String = "33860 Groesbeck Hwy, Clinton Township, MI 48035",
    var phone: String = "(586) 843-4157",
    var email: String = "service@bakersautorepair.com",
    var laborRatePerHour: BigDecimal = BigDecimal("110.00"),
    var taxRatePercent: BigDecimal = BigDecimal("6.00"),
    var warrantyText: String = "12-month / 12,000-mile parts & labor warranty.",
    var logoUri: String? = null,
    // ARI integration
    var ariEnabled: Boolean = false,
    var ariApiKey: String = "",
    var ariBaseUrl: String = "https://api.ari.app",
    var ariPath: String = "/v1/labor/estimate",
    var ariMethod: String = "GET",
    var ariHoursField: String = "hours",
    var ariConfidenceField: String = "confidence",
    var ariParamYear: String = "year",
    var ariParamMake: String = "make",
    var ariParamModel: String = "model",
    var ariParamEngine: String = "engine",
    var ariParamJob: String = "job"
)

// ==============
// Data classes
// ==============
data class Customer(var name:String="", var phone:String="", var email:String="", var address:String="")
data class Vehicle(var year:String="", var make:String="", var model:String="", var vin:String="", var engine:String="")
data class LineItem(var description:String="", var qty:BigDecimal=BigDecimal.ONE, var unitPrice:BigDecimal=BigDecimal.ZERO){
    fun lineTotal():BigDecimal=qty.multiply(unitPrice).setScale(2,RoundingMode.HALF_UP)
}
data class Invoice(
    var invoiceNumber:String=SimpleDateFormat("yyyyMMdd-HHmm",Locale.getDefault()).format(Date()),
    var date:Date=Date(),
    var customer:Customer=Customer(),
    var vehicle:Vehicle=Vehicle(),
    var laborHours:BigDecimal=BigDecimal.ZERO,
    var notes:String="",
    var lineItems:MutableList<LineItem> = mutableListOf(),
    var signature:SignatureData?=null
)
data class SignatureData(val strokes:List<List<Offset>>)

// ====================
// Helper extensions
// ====================
private fun BigDecimal.money() = NumberFormat.getCurrencyInstance().format(this)
private fun BigDecimal.pretty() = stripTrailingZeros().toPlainString()

// ====================
// Simple Signature Pad
// ====================
@Composable
fun SignaturePad(onSigned:(SignatureData?)->Unit){
    var strokes by remember { mutableStateOf<List<MutableList<Offset>>>(emptyList()) }
    var current by remember { mutableStateOf<MutableList<Offset>?>(null) }
    Box(Modifier.fillMaxWidth().height(160.dp).border(1.dp,MaterialTheme.colorScheme.outline)){
        Canvas(Modifier.fillMaxSize().pointerInput(Unit){
            detectDragGestures(
                onDragStart={o->current= mutableListOf(o); strokes=strokes+ listOf(current!!)},
                onDrag={ch,_->current?.add(ch.position)},
                onDragEnd={current=null; onSigned(SignatureData(strokes.map{it.toList()}))}
            )
        }){
            strokes.forEach{stroke->for(i in 1 until stroke.size){
                drawLine(MaterialTheme.colorScheme.onSurface,stroke[i-1],stroke[i],3f)
            }}
        }
    }
    TextButton(onClick={strokes= emptyList();onSigned(null)}){ Text("Clear") }
}

// =====================
// Labor Provider logic
// =====================
interface LaborProvider {
    suspend fun estimate(ctx:Context, vehicle:Vehicle, jobName:String, opCode:String): LaborEstimate?
}
data class LaborEstimate(val hours:BigDecimal, val source:String, val confidence:String)

class HeuristicLaborProvider:LaborProvider{
    override suspend fun estimate(ctx:Context, vehicle:Vehicle, jobName:String, opCode:String):LaborEstimate?=withContext(Dispatchers.IO){
        val base=BigDecimal("1.0")
        val factor= when{
            vehicle.make.contains("Ford",true)->BigDecimal("1.0")
            vehicle.make.contains("BMW",true)->BigDecimal("1.3")
            else->BigDecimal("1.1")
        }
        LaborEstimate(base.multiply(factor).setScale(1,RoundingMode.HALF_UP),"Heuristic","medium")
    }
}

class AriLaborProvider(private val settings:ShopSettings):LaborProvider{
    override suspend fun estimate(ctx:Context, vehicle:Vehicle, jobName:String, opCode:String):LaborEstimate?=withContext(Dispatchers.IO){
        if(!settings.ariEnabled||settings.ariApiKey.isBlank()) return@withContext null
        try{
            val params= mutableMapOf(
                settings.ariParamYear to vehicle.year,
                settings.ariParamMake to vehicle.make,
                settings.ariParamModel to vehicle.model,
                settings.ariParamJob to (opCode.ifBlank{jobName})
            ).apply{if(vehicle.engine.isNotBlank())put(settings.ariParamEngine,vehicle.engine)}

            val isPost=settings.ariMethod.equals("POST",true)
            val urlStr=if(!isPost){
                val q=params.entries.joinToString("&"){(k,v)->"$k="+URLEncoder.encode(v,"UTF-8")}
                "${settings.ariBaseUrl.trimEnd('/')}${settings.ariPath}?$q"
            }else"${settings.ariBaseUrl.trimEnd('/')}${settings.ariPath}"

            val conn=(URL(urlStr).openConnection()as HttpURLConnection).apply{
                connectTimeout=8000;readTimeout=12000
                requestMethod=if(isPost)"POST" else "GET"
                setRequestProperty("Authorization","Bearer ${settings.ariApiKey}")
                setRequestProperty("Accept","application/json")
                if(isPost){setRequestProperty("Content-Type","application/json");doOutput=true}
            }
            if(isPost){
                val body=JSONObject(); for((k,v)in params)body.put(k,v)
                conn.outputStream.use{it.write(body.toString().toByteArray())}
            }
            val status=conn.responseCode
            val text=(if(status in 200..299)conn.inputStream else conn.errorStream)?.bufferedReader()?.use{it.readText()}?:""
            conn.disconnect()
            if(status !in 200..299)return@withContext null
            val root=JSONObject(text)
            val node=when{
                root.has(settings.ariHoursField)->root
                root.has("data")->root.getJSONObject("data")
                else->root
            }
            val hoursVal=node.opt(settings.ariHoursField)
            val hours=when(hoursVal){
                is Number->BigDecimal(hoursVal.toDouble())
                is String->hoursVal.toBigDecimalOrNull()
                else->null
            }?.setScale(1,RoundingMode.HALF_UP)?:return@withContext null
            val conf=node.optString(settings.ariConfidenceField,"high")
            LaborEstimate(hours,"ARI",conf)
        }catch(e:Exception){
            withContext(Dispatchers.Main){Toast.makeText(ctx,"ARI failed: ${e.message}",Toast.LENGTH_SHORT).show()}
            null
        }
    }
}

// ================
// Simple UI shell
// ================
class MainActivity:ComponentActivity(){
    override fun onCreate(b:Bundle?){super.onCreate(b);setContent{AutoInvoiceApp()}}
}

@Composable
fun AutoInvoiceApp(){
    var settings by remember{ mutableStateOf(ShopSettings()) }
    val ctx= LocalContext.current
    val engine= remember(settings){ listOfNotNull(
        if(settings.ariEnabled) AriLaborProvider(settings) else null,
        HeuristicLaborProvider()
    )}
    Scaffold(topBar={ TopAppBar(title={ Text("AutoInvoice") }) }){
        LazyColumn(Modifier.padding(it).padding(16.dp)){
            item{ Text("Quick Quote", fontSize=20.sp, fontWeight=FontWeight.Bold) }
            var vehicle by remember{ mutableStateOf(Vehicle()) }
            OutlinedTextField(vehicle.year,{vehicle=vehicle.copy(year=it)},label={Text("Year")})
            OutlinedTextField(vehicle.make,{vehicle=vehicle.copy(make=it)},label={Text("Make")})
            OutlinedTextField(vehicle.model,{vehicle=vehicle.copy(model=it)},label={Text("Model")})
            var job by remember{ mutableStateOf("Brake Pads") }
            OutlinedTextField(job,{job=it},label={Text("Job Name")})
            var result by remember{ mutableStateOf("") }
            Button(onClick={
                CoroutineScope(Dispatchers.Main).launch{
                    var est:LaborEstimate?=null
                    for(p in engine){ est=p.estimate(ctx,vehicle,job,job); if(est!=null)break }
                    result= est?.let{"${it.hours} hrs (${it.source}, ${it.confidence})"}?:"No data"
                }
            }){ Text("Estimate Hours") }
            Text(result, fontWeight=FontWeight.SemiBold, modifier=Modifier.padding(top=8.dp))
            Divider(Modifier.padding(vertical=12.dp))
            Text("Signature:")
            var sig by remember{ mutableStateOf<SignatureData?>(null) }
            SignaturePad{sig=it}
        }
    }
}
